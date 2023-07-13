package bagguley.knes

val IRQ_NORMAL = 0
val IRQ_NMI = 1
val IRQ_RESET = 2

class Cpu(val nes: Nes) {

    lateinit var mem: IntArray
    var REG_ACC: Int = 0
    var REG_X: Int = 0
    var REG_Y: Int = 0
    var REG_SP: Int = 0
    var REG_PC: Int = 0
    var REG_PC_NEW: Int = 0
    var REG_STATUS: Int = 0
    var F_CARRY: Int = 0
    var F_DECIMAL: Int = 0
    var F_INTERRUPT: Int = 0
    var F_INTERRUPT_NEW: Int = 0
    var F_OVERFLOW: Int = 0
    var F_SIGN: Int = 0
    var F_ZERO: Int = 0
    var F_NOTUSED: Int = 0
    var F_NOTUSED_NEW: Int = 0
    var F_BRK: Int = 0
    var F_BRK_NEW: Int = 0
    var cyclesToHalt: Int = 0
    var crash:Boolean = false
    var irqRequested:Boolean = false
    var irqType:Int = 0

    fun reset() {
        mem = IntArray(0x10000)
        for (i in 0 until 0x2000) mem[i] = 0xff
        for (p in 0 until 4) {
            val i = p * 0x800
            mem[i + 0x08] = 0xf7
            mem[i + 0x09] = 0xef
            mem[i + 0x0a] = 0xdf
            mem[i + 0x0f] = 0xbf
        }
        
        for (i in 0x2001 until mem.size) mem[i] = 0
        
        // CPU Registers:
        REG_ACC = 0
        REG_X = 0
        REG_Y = 0
        // Reset Stack pointer:
        REG_SP = 0x01FF
        // Reset Program counter:
        REG_PC = 0x8000 - 1
        REG_PC_NEW = 0x8000 - 1
        // Reset Status register:
        REG_STATUS = 0x28

        setStatus(0x28)

        // Set flags:
        F_CARRY = 0
        F_DECIMAL = 0
        F_INTERRUPT = 1
        F_INTERRUPT_NEW = 1
        F_OVERFLOW = 0
        F_SIGN = 0
        F_ZERO = 1

        F_NOTUSED = 1
        F_NOTUSED_NEW = 1
        F_BRK = 1
        F_BRK_NEW = 1

        cyclesToHalt = 0

        // Reset crash flag:
        crash = false

        // Interrupt notification:
        irqRequested = false
        irqType = 0
    }

    /**
     * Emulate a single CPU instruction, returns the number of cycles
     */
    fun emulate(): Int {
        var temp: Int
        var add: Int

        if (irqRequested) {
            temp = F_CARRY or ((if (F_ZERO == 0) 1 else 0) shl 1) or (F_INTERRUPT shl 2) or
                    (F_DECIMAL shl 3) or (F_BRK shl 4) or (F_NOTUSED shl 5) or (F_OVERFLOW shl 6) or (F_SIGN shl 7)

            REG_PC_NEW = REG_PC
            F_INTERRUPT_NEW = F_INTERRUPT
            when (irqType) {
                //case 0 -> if (F_INTERRUPT != 0) 1 // Interrupt masked
                0 -> if (F_INTERRUPT == 0) doIrq(temp)
                1 -> doNonMaskableInterrupt(temp)
                2 -> doResetInterrupt()
            }
            REG_PC = REG_PC_NEW
            F_INTERRUPT = F_INTERRUPT_NEW
            F_BRK = F_BRK_NEW
            irqRequested = false
        }
        val opinf: Int = OpData.getOp(nes.mmap.get().load(REG_PC + 1))
        var cycleCount: Int = (opinf shr 24)
        var cycleAdd: Int = 0

        // Find address mode
        val addrMode = (opinf shr 8) and 0xff

        // Increment PC by number of op bytes
        val opaddr = REG_PC
        REG_PC += ((opinf shr 16) and 0xff)

        var addr:Int = 0
        when (addrMode) {
            // Zero page mode, use the address given after the opcode
            // but without the high byte
            0 -> addr = load(opaddr + 2)
            1 -> {
                addr = load(opaddr + 2)
                addr += if (addr < 0x80) {
                    REG_PC
                } else {
                    REG_PC-256
                }
            }
            2 -> {/* Ignore, address is implied in instruction */}
            3 -> addr = load16bit(opaddr+2)
            // Accumulator mode. The address is in the accumulator
            4 -> addr = REG_ACC
            // Immediate mode. The value is given after the opcode
            5 -> addr = REG_PC
            // Zero page indexed mode, X as index. Use the address given
            // after the opcode, then add the X register to it to get the
            // final address
            6 -> addr = (load(opaddr+2)+REG_X) and 0xff
            // Zero page index mode, Y as index. Use the address given
            // after the opcode, then add the Y register to it to get the
            // final address
            7 -> addr = (load(opaddr+2)+REG_Y) and 0xff
            // Absolute indexed mode, X as index. Same as zero page indexed,
            // but with the high byte
            8 -> {
                addr = load16bit(opaddr+2)
                if ((addr and 0xff00) != ((addr+REG_X) and 0xff00)) cycleAdd = 1
                addr += REG_X
            }
            // Absolute indexed mode, Y as index. Same as zero page indexed
            // but with the high byte
            9 -> {
                addr = load16bit(opaddr+2)
                if ((addr and 0xff00) != ((addr+REG_Y) and 0xff00)) cycleAdd = 1
                addr += REG_Y
            }
            // Pre-indexed indirect mode. Find the 16-bit address starting at
            // the given location plus the current X register. The value is the
            // contents of that address
            10 -> {
                addr = load(opaddr+2)
                if ((addr and 0xff00) != ((addr+REG_X) and 0xff00)) cycleAdd = 1
                addr += REG_X
                addr = addr and 0xff
                addr = load16bit(addr)
            }
            // Post indexed indirect mode. Find the 16-bit address contained in the given
            // location (and the one following), add to that address the contents of the Y
            // register, fetch the value stored at that address
            11 -> {
                addr = load16bit(load(opaddr+2))
                if ((addr and 0xFF00) != ((addr+REG_Y) and 0xFF00)) cycleAdd = 1
                addr += REG_Y
            }
            // Indirect Absolute mode. Find the 16-bit address contained at the given location.
            12 -> {
                addr = load16bit(opaddr+2)// Find op
                addr = if (addr < 0x1FFF) {
                    mem[addr] + (mem[(addr and 0xFF00) or (((addr and 0xFF) + 1) and 0xFF)] shl 8) // Read from address given in op
                } else {
                    nes.mmap.get().load(addr) + (nes.mmap.get().load((addr and 0xFF00) or (((addr and 0xFF) + 1) and 0xFF)) shl 8)
                }
            }
        }
        addr = addr and 0xffff

        // ==============================
        // Decode and execute instruction
        // ==============================

        // This should be compiled into a jump table
        when (opinf and 0xff) {
            0 -> {
                // ADC
                temp = REG_ACC + load(addr) + F_CARRY
                F_OVERFLOW = if (!(((REG_ACC xor load(addr)) and 0x80)!=0) && ((REG_ACC xor temp) and 0x80)!=0) 1 else 0
                F_CARRY = if (temp>255) 1 else 0
                F_SIGN = (temp shr 7) and 1
                F_ZERO = temp and 0xFF
                REG_ACC = temp and 255
                cycleCount += cycleAdd
            }
            1 -> {
                // AND
                REG_ACC = REG_ACC and load(addr)
                F_SIGN = (REG_ACC shr 7) and 1
                F_ZERO = REG_ACC
                //REG_ACC = temp
                if (addrMode!=11) cycleCount += cycleAdd // PostIdxInd = 11
            }
            2 -> {
                // ASL
                // Shift left one bit
                if (addrMode == 4) { // ADDR_ACC = 4
                    F_CARRY = (REG_ACC shr 7) and 1
                    REG_ACC = (REG_ACC shl 1) and 255
                    F_SIGN = (REG_ACC shr 7) and 1
                    F_ZERO = REG_ACC
                } else {
                    temp = load(addr)
                    F_CARRY = (temp shr 7) and 1
                    temp = (temp shl 1) and 255
                    F_SIGN = (temp shr 7) and 1
                    F_ZERO = temp
                    write(addr, temp)
                }
            }
            3 -> {
                // BCC
                // Branch on carry clear
                if (F_CARRY == 0) {
                    cycleCount += (if ((opaddr and 0xFF00) != (addr and 0xFF00)) 2 else 1)
                    REG_PC = addr
                }
            }
            4 -> {
                // BCS
                // Branch on carry set
                if (F_CARRY == 1){
                    cycleCount += (if ((opaddr and 0xFF00) != (addr and 0xFF00)) 2 else 1)
                    REG_PC = addr;
                }
            }
            5 -> {
                // BEQ
                // Branch on zero
                if (F_ZERO == 0) {
                    cycleCount += (if ((opaddr and 0xFF00) != (addr and 0xFF00)) 2 else 1)
                    REG_PC = addr;
                }
            }
            6 -> {
                // BIT
                temp = load(addr)
                F_SIGN = (temp shr 7) and 1
                F_OVERFLOW = (temp shr 6) and 1
                temp = temp and REG_ACC
                F_ZERO = temp
            }
            7 -> {
                // BMI
                // Branch on negative result
                if (F_SIGN == 1) {
                    cycleCount +=1
                    REG_PC = addr
                }
            }
            8 -> {
                // BNE
                // Branch on not zero
                if (F_ZERO != 0) {
                    cycleCount += (if ((opaddr and 0xFF00) != (addr and 0xFF00)) 2 else 1)
                    REG_PC = addr
                }
            }
            9 -> {
                // BPL
                // Branch on positive result
                if (F_SIGN == 0) {
                    cycleCount += (if ((opaddr and 0xFF00) != (addr and 0xFF00)) 2 else 1)
                    REG_PC = addr
                }
            }
            10 -> {
                // BRK
                REG_PC += 2
                push((REG_PC shr 8) and 255)
                push(REG_PC and 255)
                F_BRK = 1

                push(
                    (F_CARRY) or
                    ((if (F_ZERO==0) 1 else 0) shl 1) or
                    (F_INTERRUPT shl 2) or
                    (F_DECIMAL shl 3) or
                    (F_BRK shl 4) or
                    (F_NOTUSED shl 5) or
                    (F_OVERFLOW shl 6) or
                    (F_SIGN shl 7)
                )

                F_INTERRUPT = 1
                //REG_PC = load(0xFFFE) or (load(0xFFFF) shl 8)
                REG_PC = load16bit(0xFFFE)
                REG_PC -= 1
            }
            11 -> {
                // BVC
                // Branch on overflow clear
                if (F_OVERFLOW == 0) {
                    cycleCount += (if ((opaddr and 0xFF00) != (addr and 0xFF00)) 2 else 1)
                    REG_PC = addr
                }
            }
            12 -> {
                // BVS
                // Branch on overflow set
                if (F_OVERFLOW == 1) {
                    cycleCount += (if ((opaddr and 0xFF00) != (addr and 0xFF00)) 2 else 1)
                    REG_PC = addr
                }
            }
            13 -> {
                // CLC
                // Clear carry flag
                F_CARRY = 0
            }
            14 -> {
                // CLD
                // Clear decimal flag
                F_DECIMAL = 0
            }
            15 -> {
                // CLI
                // Clear interrupt flag
                F_INTERRUPT = 0
            }
            16 -> {
                // CLV
                // Clear overflow flag
                F_OVERFLOW = 0
            }
            17 -> {
                // CMP
                // Compare memory with accumulator
                temp = REG_ACC - load(addr)
                F_CARRY = if (temp>=0) 1 else 0
                F_SIGN = (temp shr 7) and 1
                F_ZERO = temp and 0xFF
                cycleCount += cycleAdd
            }
            18 -> {
                // CPX
                // Compare memory and index X
                temp = REG_X - load(addr)
                F_CARRY = if (temp >= 0) 1 else 0
                F_SIGN = (temp shr 7) and 1
                F_ZERO = temp and 0xFF
            }
            19 -> {
                // CPY
                // Compare memory and index Y:
                temp = REG_Y - load(addr)
                F_CARRY = if (temp >= 0) 1 else 0
                F_SIGN = (temp shr 7) and 1
                F_ZERO = temp and 0xFF
            }
            20 -> {
                // DEC
                // Decrement memory by one
                temp = (load(addr)-1) and 0xFF
                F_SIGN = (temp shr 7) and 1
                F_ZERO = temp
                write(addr, temp)
            }
            21 -> {
                // DECX
                // Decrement index X by one:
                REG_X = (REG_X - 1) and 0xFF
                F_SIGN = (REG_X shr 7) and 1
                F_ZERO = REG_X
            }
            22 -> {
                // DECY
                // Decrement index Y by one:
                REG_Y = (REG_Y-1) and 0xFF
                F_SIGN = (REG_Y shr 7) and 1
                F_ZERO = REG_Y
            }
            23 -> {
                // EOR
                // XOR Memory with accumulator, store in accumulator:
                REG_ACC = (load(addr) xor REG_ACC) and 0xFF
                F_SIGN = (REG_ACC shr 7) and 1
                F_ZERO = REG_ACC
                cycleCount += cycleAdd
            }
            24 -> {
                // INC
                // Increment memory by one:
                temp = (load(addr) + 1) and 0xFF
                F_SIGN = (temp shr 7) and 1
                F_ZERO = temp
                write(addr, temp and 0xFF)
            }
            25 -> {
                // INX
                // Increment index X by one:
                REG_X = (REG_X + 1) and 0xFF
                F_SIGN = (REG_X shr 7) and 1
                F_ZERO = REG_X
            }
            26 -> {
                // INY
                // Increment index Y by one:
                REG_Y += 1
                REG_Y = REG_Y and 0xFF
                F_SIGN = (REG_Y shr 7) and 1
                F_ZERO = REG_Y
            }
            27 -> {
                // JMP
                // Jump to new location
                REG_PC = addr-1
            }
            28 -> {
                // JSR
                // Jump to new location, saving return address.
                // Push return address on stack:
                push((REG_PC shr 8) and 255)
                push(REG_PC and 255)
                REG_PC = addr - 1
            }
            29 -> {
                // LDA
                // Load accumulator with memory:
                REG_ACC = load(addr)
                F_SIGN = (REG_ACC shr 7) and 1
                F_ZERO = REG_ACC
                cycleCount += cycleAdd
            }
            30 -> {
                // LDX
                // Load index X with memory:
                REG_X = load(addr)
                F_SIGN = (REG_X shr 7) and 1
                F_ZERO = REG_X
                cycleCount += cycleAdd
            }
            31 -> {
                // LDY
                // Load index Y with memory:
                REG_Y = load(addr)
                F_SIGN = (REG_Y shr 7) and 1
                F_ZERO = REG_Y
                cycleCount += cycleAdd;
            }
            32 -> {
                // LSR
                // Shift right one bit:
                if (addrMode == 4) { // ADDR_ACC
                    temp = (REG_ACC and 0xFF)
                    F_CARRY = temp and 1
                    temp = temp shr 1
                    REG_ACC = temp
                } else {
                    temp = load(addr) and 0xFF
                    F_CARRY = temp and 1
                    temp = temp shr 1
                    write(addr, temp)
                }
                F_SIGN = 0
                F_ZERO = temp
            }
            33 -> {
                // NOP
            }
            34 -> {
                // ORA
                // OR memory with accumulator, store in accumulator.
                temp = (load(addr) or REG_ACC) and 255
                F_SIGN = (temp shr 7) and 1
                F_ZERO = temp
                REG_ACC = temp
                if (addrMode != 11) cycleCount += cycleAdd // PostIdxInd = 11
            }
            35 -> {
                // PHA
                // Push accumulator on stack
                push(REG_ACC)
            }
            36 -> {
                // PHP
                // Push processor status on stack
                F_BRK = 1
                push(
                    (F_CARRY) or
                    ((if (F_ZERO==0) 1 else 0) shl 1) or
                    (F_INTERRUPT shl 2) or
                    (F_DECIMAL shl 3) or
                    (F_BRK shl 4) or
                    (F_NOTUSED shl 5) or
                    (F_OVERFLOW shl 6) or
                    (F_SIGN shl 7)
                )
            }
            37 -> {
                // PLA
                // Pull accumulator from stack
                REG_ACC = pull()
                F_SIGN = (REG_ACC shr 7) and 1
                F_ZERO = REG_ACC
            }
            38 -> {
                // PLP
                // Pull processor status from stack
                temp = pull()
                F_CARRY     = (temp) and 1
                F_ZERO      = if (((temp shr 1) and 1) == 1) 0 else 1
                F_INTERRUPT = (temp shr 2) and 1
                F_DECIMAL   = (temp shr 3) and 1
                F_BRK       = (temp shr 4) and 1
                F_NOTUSED   = (temp shr 5) and 1
                F_OVERFLOW  = (temp shr 6) and 1
                F_SIGN      = (temp shr 7) and 1

                F_NOTUSED = 1;
            }
            39 -> {
                // ROL
                // Rotate one bit left
                if (addrMode == 4) { // ADDR_ACC = 4
                    temp = REG_ACC
                    add = F_CARRY
                    F_CARRY = (temp shr 7) and 1
                    temp = ((temp shl 1) and 0xFF) + add
                    REG_ACC = temp
                } else {
                    temp = load(addr)
                    add = F_CARRY
                    F_CARRY = (temp shr 7) and 1
                    temp = ((temp shl 1) and 0xFF) + add
                    write(addr, temp)
                }
                F_SIGN = (temp shr 7) and 1
                F_ZERO = temp
            }
            40 -> {
                // ROR
                // Rotate one bit right
                if (addrMode == 4){ // ADDR_ACC = 4
                    add = F_CARRY shl 7
                    F_CARRY = REG_ACC and 1
                    temp = (REG_ACC shr 1) + add
                    REG_ACC = temp
                } else {
                    temp = load(addr)
                    add = F_CARRY shl 7
                    F_CARRY = temp and 1
                    temp = (temp shr 1) + add
                    write(addr, temp)
                }
                F_SIGN = (temp shr 7) and 1
                F_ZERO = temp
            }
            41 -> {
                // RTI
                // Return from interrupt. Pull status and PC from stack.

                temp = pull()
                F_CARRY     = (temp   ) and 1
                F_ZERO      = if (((temp shr 1) and 1) == 0) 1 else 0
                F_INTERRUPT = (temp shr 2) and 1
                F_DECIMAL   = (temp shr 3) and 1
                F_BRK       = (temp shr 4) and 1
                F_NOTUSED   = (temp shr 5) and 1
                F_OVERFLOW  = (temp shr 6) and 1
                F_SIGN      = (temp shr 7) and 1

                REG_PC = pull()
                REG_PC += (pull() shl 8)

                if (REG_PC == 0xFFFF) {
                    //return;
                } else {
                    REG_PC -= 1
                    F_NOTUSED = 1
                }
            }
            42 -> {
                // RTS
                // Return from subroutine. Pull PC from stack.
                REG_PC = pull()
                REG_PC += (pull() shl 8)

                if (REG_PC == 0xFFFF) {
                    // TODO
                    //return; // return from NSF play routine:
                }
            }
            43 -> {
                // SBC
                temp = REG_ACC - load(addr) - (1 - F_CARRY)
                F_SIGN = (temp shr 7) and 1
                F_ZERO = temp and 0xFF
                F_OVERFLOW = if (((REG_ACC xor temp) and 0x80) !=0 && ((REG_ACC xor load(addr)) and 0x80) !=0) 1 else 0
                F_CARRY = if (temp < 0) 0 else 1
                REG_ACC = (temp and 0xFF)
                if (addrMode != 11) cycleCount += cycleAdd // PostIdxInd = 11
            }
            44 -> {
                // SEC
                // Set carry flag
                F_CARRY = 1
            }
            45 -> {
                // SED
                // Set decimal mode
                F_DECIMAL = 1
            }
            46 -> {
                // SEI
                // Set interrupt disable status
                F_INTERRUPT = 1
            }
            47 -> {
                // STA
                // Store accumulator in memory
                write(addr, REG_ACC)
            }
            48 -> {
                // STX
                // Store index X in memory
                write(addr, REG_X)
            }
            49 -> {
                // STY
                // Store index Y in memory:
                write(addr, REG_Y)
            }
            50 -> {
                // TAX
                // Transfer accumulator to index X:
                REG_X = REG_ACC
                F_SIGN = (REG_ACC shr 7) and 1
                F_ZERO = REG_ACC
            }
            51 -> {
                // TAY
                // Transfer accumulator to index Y:
                REG_Y = REG_ACC;
                F_SIGN = (REG_ACC shr 7) and 1
                F_ZERO = REG_ACC
            }
            52 -> {
                // TSX
                // Transfer stack pointer to index X:
                REG_X = (REG_SP - 0x0100)
                F_SIGN = (REG_SP shr 7) and 1
                F_ZERO = REG_X
            }
            53 -> {
                // TXA
                // Transfer index X to accumulator:
                REG_ACC = REG_X
                F_SIGN = (REG_X shr 7) and 1
                F_ZERO = REG_X
            }
            54 -> {
                // TXS
                // Transfer index X to stack pointer:
                REG_SP = REG_X + 0x0100
                stackWrap()
            }
            55 -> {
                // TYA
                // Transfer index Y to accumulator:
                REG_ACC = REG_Y
                F_SIGN = (REG_Y shr 7) and 1
                F_ZERO = REG_Y
            }
            else -> {
                // Unknown OP
                nes.stop("Game crashed, invalid opcode at address: " + Integer.toHexString(opaddr))
            }
        }

        return cycleCount
    }

    fun load(addr: Int): Int {
        return if (addr < 0x2000) {
            mem[addr and 0x7FF]
        } else {
            nes.mmap.get().load(addr)
        }
    }

    fun load16bit(addr: Int): Int {
        return if (addr < 0x1FFF) {
            mem[addr and 0x7FF] or (mem[(addr + 1) and 0x7FF] shl 8)
        } else {
            nes.mmap.get().load(addr) or (nes.mmap.get().load(addr+1) shl 8)
        }
    }

    fun write(addr: Int, value: Int) {
        if (addr < 0x2000) {
            mem[addr and 0x7FF] = value
        }
        else {
            nes.mmap.get().write(addr, value)
        }
    }

    fun requestIrq(irqTypeInput: Int) {
        if (irqRequested) {
            if (irqTypeInput == IRQ_NORMAL) {
                // Nothing
            } else {
                irqRequested = true
                irqType = irqTypeInput
            }
            ////System.out.println("too fast irqs. type="+type);
        } else {
            irqRequested = true
            irqType = irqTypeInput
        }
    }

    fun push(value:Int) {
        nes.mmap.get().write(REG_SP, value)
        REG_SP -= 1
        REG_SP = 0x0100 or (REG_SP and 0xFF)
    }

    fun stackWrap() {
        REG_SP = 0x0100 or (REG_SP and 0xFF)
    }

    fun pull(): Int {
        REG_SP += 1
        REG_SP = 0x0100 or (REG_SP and 0xFF)
        return nes.mmap.get().load(REG_SP)
    }

    fun pageCrossed(addr1: Int, addr2: Int):Boolean {
        return ((addr1 and 0xFF00) != (addr2 and 0xFF00))
    }

    fun haltCycles(cycles: Int) {
        cyclesToHalt += cycles
    }

    fun doNonMaskableInterrupt(status:Int) {
        // Check whether VBlank Interrupts are enabled
        if((nes.mmap.get().load(0x2000) and 128) != 0) {
            REG_PC_NEW += 1
            push((REG_PC_NEW shr 8) and 0xFF)
            push(REG_PC_NEW and 0xFF)
            //F_INTERRUPT_NEW = 1
            push(status)

            REG_PC_NEW = nes.mmap.get().load(0xFFFA) or (nes.mmap.get().load(0xFFFB) shl 8)
            REG_PC_NEW -= 1
        }
    }

    fun doResetInterrupt() {
        REG_PC_NEW = nes.mmap.get().load(0xFFFC) or (nes.mmap.get().load(0xFFFD) shl 8)
        REG_PC_NEW -= 1
    }

    fun doIrq(status:Int) {
        REG_PC_NEW += 1
        push((REG_PC_NEW shr 8) and 0xFF)
        push(REG_PC_NEW and 0xFF)
        push(status)
        F_INTERRUPT_NEW = 1
        F_BRK_NEW = 0

        REG_PC_NEW = nes.mmap.get().load(0xFFFE) or (nes.mmap.get().load(0xFFFF) shl 8)
        REG_PC_NEW -= 1
    }

    fun getStatus():Int {
        return (F_CARRY) or (F_ZERO shl 1) or (F_INTERRUPT shl 2) or (F_DECIMAL shl 3) or (F_BRK shl 4) or
                (F_NOTUSED shl 5) or (F_OVERFLOW shl 6) or (F_SIGN shl 7)
    }

    fun setStatus(st:Int) {
        F_CARRY     = (st   ) and 1
        F_ZERO      = (st shr 1) and 1
        F_INTERRUPT = (st shr 2) and 1
        F_DECIMAL   = (st shr 3) and 1
        F_BRK       = (st shr 4) and 1
        F_NOTUSED   = (st shr 5) and 1
        F_OVERFLOW  = (st shr 6) and 1
        F_SIGN      = (st shr 7) and 1
    }
}