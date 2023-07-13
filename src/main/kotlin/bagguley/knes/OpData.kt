package bagguley.knes

// Provides details about instructions
object OpData {
    val INS_ADC = 0
    val INS_AND = 1
    val INS_ASL = 2

    val INS_BCC = 3
    val INS_BCS = 4
    val INS_BEQ = 5
    val INS_BIT = 6
    val INS_BMI = 7
    val INS_BNE = 8
    val INS_BPL = 9
    val INS_BRK = 10
    val INS_BVC = 11
    val INS_BVS = 12

    val INS_CLC = 13
    val INS_CLD = 14
    val INS_CLI = 15
    val INS_CLV = 16
    val INS_CMP = 17
    val INS_CPX = 18
    val INS_CPY = 19

    val INS_DEC = 20
    val INS_DEX = 21
    val INS_DEY = 22

    val INS_EOR = 23

    val INS_INC = 24
    val INS_INX = 25
    val INS_INY = 26

    val INS_JMP = 27
    val INS_JSR = 28

    val INS_LDA = 29
    val INS_LDX = 30
    val INS_LDY = 31
    val INS_LSR = 32

    val INS_NOP = 33

    val INS_ORA = 34

    val INS_PHA = 35
    val INS_PHP = 36
    val INS_PLA = 37
    val INS_PLP = 38

    val INS_ROL = 39
    val INS_ROR = 40
    val INS_RTI = 41
    val INS_RTS = 42

    val INS_SBC = 43
    val INS_SEC = 44
    val INS_SED = 45
    val INS_SEI = 46
    val INS_STA = 47
    val INS_STX = 48
    val INS_STY = 49

    val INS_TAX = 50
    val INS_TAY = 51
    val INS_TSX = 52
    val INS_TXA = 53
    val INS_TXS = 54
    val INS_TYA = 55

    val INS_DUMMY = 56 // dummy instruction used for 'halting' the processor some cycles

    // -------------------------------- //

    // Addressing modes:
    val ADDR_ZP = 0
    val ADDR_REL = 1
    val ADDR_IMP = 2
    val ADDR_ABS = 3
    val ADDR_ACC = 4
    val ADDR_IMM = 5
    val ADDR_ZPX = 6
    val ADDR_ZPY = 7
    val ADDR_ABSX = 8
    val ADDR_ABSY = 9
    val ADDR_PREIDXIND = 10
    val ADDR_POSTIDXIND = 11
    val ADDR_INDABS = 12

    var opdata = IntArray(256)

    val instname = Array(56) { "" }


    val cycTable = listOf(
        /*0x00*/ 7, 6, 2, 8, 3, 3, 5, 5, 3, 2, 2, 2, 4, 4, 6, 6,
        /*0x10*/ 2, 5, 2, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7,
        /*0x20*/ 6, 6, 2, 8, 3, 3, 5, 5, 4, 2, 2, 2, 4, 4, 6, 6,
        /*0x30*/ 2, 5, 2, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7,
        /*0x40*/ 6, 6, 2, 8, 3, 3, 5, 5, 3, 2, 2, 2, 3, 4, 6, 6,
        /*0x50*/ 2, 5, 2, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7,
        /*0x60*/ 6, 6, 2, 8, 3, 3, 5, 5, 4, 2, 2, 2, 5, 4, 6, 6,
        /*0x70*/ 2, 5, 2, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7,
        /*0x80*/ 2, 6, 2, 6, 3, 3, 3, 3, 2, 2, 2, 2, 4, 4, 4, 4,
        /*0x90*/ 2, 6, 2, 6, 4, 4, 4, 4, 2, 5, 2, 5, 5, 5, 5, 5,
        /*0xA0*/ 2, 6, 2, 6, 3, 3, 3, 3, 2, 2, 2, 2, 4, 4, 4, 4,
        /*0xB0*/ 2, 5, 2, 5, 4, 4, 4, 4, 2, 4, 2, 4, 4, 4, 4, 4,
        /*0xC0*/ 2, 6, 2, 8, 3, 3, 5, 5, 2, 2, 2, 2, 4, 4, 6, 6,
        /*0xD0*/ 2, 5, 2, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7,
        /*0xE0*/ 2, 6, 3, 8, 3, 3, 5, 5, 2, 2, 2, 2, 4, 4, 6, 6,
        /*0xF0*/ 2, 5, 2, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7)

    val addrDesc = listOf(
        "Zero Page           ",
        "Relative            ",
        "Implied             ",
        "Absolute            ",
        "Accumulator         ",
        "Immediate           ",
        "Zero Page,X         ",
        "Zero Page,Y         ",
        "Absolute,X          ",
        "Absolute,Y          ",
        "Preindexed Indirect ",
        "Postindexed Indirect",
        "Indirect Absolute   ")

    init {

        // Set all to invalid instruction (to detect crashes):
        for (i in 0 until 256) opdata[i] = 0xFF

        // Now fill in all valid opcodes:

        // ADC:
        setOp(OpData.INS_ADC, 0x69, OpData.ADDR_IMM, 2, 2)
        setOp(OpData.INS_ADC, 0x65, OpData.ADDR_ZP, 2, 3)
        setOp(OpData.INS_ADC, 0x75, OpData.ADDR_ZPX, 2, 4)
        setOp(OpData.INS_ADC, 0x6D, OpData.ADDR_ABS, 3, 4)
        setOp(OpData.INS_ADC, 0x7D, OpData.ADDR_ABSX, 3, 4)
        setOp(OpData.INS_ADC, 0x79, OpData.ADDR_ABSY, 3, 4)
        setOp(OpData.INS_ADC, 0x61, OpData.ADDR_PREIDXIND, 2, 6)
        setOp(OpData.INS_ADC, 0x71, OpData.ADDR_POSTIDXIND, 2, 5)

        // AND:
        setOp(OpData.INS_AND, 0x29, OpData.ADDR_IMM, 2, 2)
        setOp(OpData.INS_AND, 0x25, OpData.ADDR_ZP, 2, 3)
        setOp(OpData.INS_AND, 0x35, OpData.ADDR_ZPX, 2, 4)
        setOp(OpData.INS_AND, 0x2D, OpData.ADDR_ABS, 3, 4)
        setOp(OpData.INS_AND, 0x3D, OpData.ADDR_ABSX, 3, 4)
        setOp(OpData.INS_AND, 0x39, OpData.ADDR_ABSY, 3, 4)
        setOp(OpData.INS_AND, 0x21, OpData.ADDR_PREIDXIND, 2, 6)
        setOp(OpData.INS_AND, 0x31, OpData.ADDR_POSTIDXIND, 2, 5)

        // ASL:
        setOp(OpData.INS_ASL, 0x0A, OpData.ADDR_ACC, 1, 2)
        setOp(OpData.INS_ASL, 0x06, OpData.ADDR_ZP, 2, 5)
        setOp(OpData.INS_ASL, 0x16, OpData.ADDR_ZPX, 2, 6)
        setOp(OpData.INS_ASL, 0x0E, OpData.ADDR_ABS, 3, 6)
        setOp(OpData.INS_ASL, 0x1E, OpData.ADDR_ABSX, 3, 7)

        // BCC:
        setOp(OpData.INS_BCC, 0x90, OpData.ADDR_REL, 2, 2)

        // BCS:
        setOp(OpData.INS_BCS, 0xB0, OpData.ADDR_REL, 2, 2)

        // BEQ:
        setOp(OpData.INS_BEQ, 0xF0, OpData.ADDR_REL, 2, 2)

        // BIT:
        setOp(OpData.INS_BIT, 0x24, OpData.ADDR_ZP, 2, 3)
        setOp(OpData.INS_BIT, 0x2C, OpData.ADDR_ABS, 3, 4)

        // BMI:
        setOp(OpData.INS_BMI, 0x30, OpData.ADDR_REL, 2, 2)

        // BNE:
        setOp(OpData.INS_BNE, 0xD0, OpData.ADDR_REL, 2, 2)

        // BPL:
        setOp(OpData.INS_BPL, 0x10, OpData.ADDR_REL, 2, 2)

        // BRK:
        setOp(OpData.INS_BRK, 0x00, OpData.ADDR_IMP, 1, 7)

        // BVC:
        setOp(OpData.INS_BVC, 0x50, OpData.ADDR_REL, 2, 2)

        // BVS:
        setOp(OpData.INS_BVS, 0x70, OpData.ADDR_REL, 2, 2)

        // CLC:
        setOp(OpData.INS_CLC, 0x18, OpData.ADDR_IMP, 1, 2)

        // CLD:
        setOp(OpData.INS_CLD, 0xD8, OpData.ADDR_IMP, 1, 2)

        // CLI:
        setOp(OpData.INS_CLI, 0x58, OpData.ADDR_IMP, 1, 2)

        // CLV:
        setOp(OpData.INS_CLV, 0xB8, OpData.ADDR_IMP, 1, 2)

        // CMP:
        setOp(OpData.INS_CMP, 0xC9, OpData.ADDR_IMM, 2, 2)
        setOp(OpData.INS_CMP, 0xC5, OpData.ADDR_ZP, 2, 3)
        setOp(OpData.INS_CMP, 0xD5, OpData.ADDR_ZPX, 2, 4)
        setOp(OpData.INS_CMP, 0xCD, OpData.ADDR_ABS, 3, 4)
        setOp(OpData.INS_CMP, 0xDD, OpData.ADDR_ABSX, 3, 4)
        setOp(OpData.INS_CMP, 0xD9, OpData.ADDR_ABSY, 3, 4)
        setOp(OpData.INS_CMP, 0xC1, OpData.ADDR_PREIDXIND, 2, 6)
        setOp(OpData.INS_CMP, 0xD1, OpData.ADDR_POSTIDXIND, 2, 5)

        // CPX:
        setOp(OpData.INS_CPX, 0xE0, OpData.ADDR_IMM, 2, 2)
        setOp(OpData.INS_CPX, 0xE4, OpData.ADDR_ZP, 2, 3)
        setOp(OpData.INS_CPX, 0xEC, OpData.ADDR_ABS, 3, 4)

        // CPY:
        setOp(OpData.INS_CPY, 0xC0, OpData.ADDR_IMM, 2, 2)
        setOp(OpData.INS_CPY, 0xC4, OpData.ADDR_ZP, 2, 3)
        setOp(OpData.INS_CPY, 0xCC, OpData.ADDR_ABS, 3, 4)

        // DEC:
        setOp(OpData.INS_DEC, 0xC6, OpData.ADDR_ZP, 2, 5)
        setOp(OpData.INS_DEC, 0xD6, OpData.ADDR_ZPX, 2, 6)
        setOp(OpData.INS_DEC, 0xCE, OpData.ADDR_ABS, 3, 6)
        setOp(OpData.INS_DEC, 0xDE, OpData.ADDR_ABSX, 3, 7)

        // DEX:
        setOp(OpData.INS_DEX, 0xCA, OpData.ADDR_IMP, 1, 2)

        // DEY:
        setOp(OpData.INS_DEY, 0x88, OpData.ADDR_IMP, 1, 2)

        // EOR:
        setOp(OpData.INS_EOR, 0x49, OpData.ADDR_IMM, 2, 2)
        setOp(OpData.INS_EOR, 0x45, OpData.ADDR_ZP, 2, 3)
        setOp(OpData.INS_EOR, 0x55, OpData.ADDR_ZPX, 2, 4)
        setOp(OpData.INS_EOR, 0x4D, OpData.ADDR_ABS, 3, 4)
        setOp(OpData.INS_EOR, 0x5D, OpData.ADDR_ABSX, 3, 4)
        setOp(OpData.INS_EOR, 0x59, OpData.ADDR_ABSY, 3, 4)
        setOp(OpData.INS_EOR, 0x41, OpData.ADDR_PREIDXIND, 2, 6)
        setOp(OpData.INS_EOR, 0x51, OpData.ADDR_POSTIDXIND, 2, 5)

        // INC:
        setOp(OpData.INS_INC, 0xE6, OpData.ADDR_ZP, 2, 5)
        setOp(OpData.INS_INC, 0xF6, OpData.ADDR_ZPX, 2, 6)
        setOp(OpData.INS_INC, 0xEE, OpData.ADDR_ABS, 3, 6)
        setOp(OpData.INS_INC, 0xFE, OpData.ADDR_ABSX, 3, 7)

        // INX:
        setOp(OpData.INS_INX, 0xE8, OpData.ADDR_IMP, 1, 2)

        // INY:
        setOp(OpData.INS_INY, 0xC8, OpData.ADDR_IMP, 1, 2)

        // JMP:
        setOp(OpData.INS_JMP, 0x4C, OpData.ADDR_ABS, 3, 3)
        setOp(OpData.INS_JMP, 0x6C, OpData.ADDR_INDABS, 3, 5)

        // JSR:
        setOp(OpData.INS_JSR, 0x20, OpData.ADDR_ABS, 3, 6)

        // LDA:
        setOp(OpData.INS_LDA, 0xA9, OpData.ADDR_IMM, 2, 2)
        setOp(OpData.INS_LDA, 0xA5, OpData.ADDR_ZP, 2, 3)
        setOp(OpData.INS_LDA, 0xB5, OpData.ADDR_ZPX, 2, 4)
        setOp(OpData.INS_LDA, 0xAD, OpData.ADDR_ABS, 3, 4)
        setOp(OpData.INS_LDA, 0xBD, OpData.ADDR_ABSX, 3, 4)
        setOp(OpData.INS_LDA, 0xB9, OpData.ADDR_ABSY, 3, 4)
        setOp(OpData.INS_LDA, 0xA1, OpData.ADDR_PREIDXIND, 2, 6)
        setOp(OpData.INS_LDA, 0xB1, OpData.ADDR_POSTIDXIND, 2, 5)

        // LDX:
        setOp(OpData.INS_LDX, 0xA2, OpData.ADDR_IMM, 2, 2)
        setOp(OpData.INS_LDX, 0xA6, OpData.ADDR_ZP, 2, 3)
        setOp(OpData.INS_LDX, 0xB6, OpData.ADDR_ZPY, 2, 4)
        setOp(OpData.INS_LDX, 0xAE, OpData.ADDR_ABS, 3, 4)
        setOp(OpData.INS_LDX, 0xBE, OpData.ADDR_ABSY, 3, 4)

        // LDY:
        setOp(OpData.INS_LDY, 0xA0, OpData.ADDR_IMM, 2, 2)
        setOp(OpData.INS_LDY, 0xA4, OpData.ADDR_ZP, 2, 3)
        setOp(OpData.INS_LDY, 0xB4, OpData.ADDR_ZPX, 2, 4)
        setOp(OpData.INS_LDY, 0xAC, OpData.ADDR_ABS, 3, 4)
        setOp(OpData.INS_LDY, 0xBC, OpData.ADDR_ABSX, 3, 4)

        // LSR:
        setOp(OpData.INS_LSR, 0x4A, OpData.ADDR_ACC, 1, 2)
        setOp(OpData.INS_LSR, 0x46, OpData.ADDR_ZP, 2, 5)
        setOp(OpData.INS_LSR, 0x56, OpData.ADDR_ZPX, 2, 6)
        setOp(OpData.INS_LSR, 0x4E, OpData.ADDR_ABS, 3, 6)
        setOp(OpData.INS_LSR, 0x5E, OpData.ADDR_ABSX, 3, 7)

        // NOP:
        setOp(OpData.INS_NOP, 0xEA, OpData.ADDR_IMP, 1, 2)

        // ORA:
        setOp(OpData.INS_ORA, 0x09, OpData.ADDR_IMM, 2, 2)
        setOp(OpData.INS_ORA, 0x05, OpData.ADDR_ZP, 2, 3)
        setOp(OpData.INS_ORA, 0x15, OpData.ADDR_ZPX, 2, 4)
        setOp(OpData.INS_ORA, 0x0D, OpData.ADDR_ABS, 3, 4)
        setOp(OpData.INS_ORA, 0x1D, OpData.ADDR_ABSX, 3, 4)
        setOp(OpData.INS_ORA, 0x19, OpData.ADDR_ABSY, 3, 4)
        setOp(OpData.INS_ORA, 0x01, OpData.ADDR_PREIDXIND, 2, 6)
        setOp(OpData.INS_ORA, 0x11, OpData.ADDR_POSTIDXIND, 2, 5)

        // PHA:
        setOp(OpData.INS_PHA, 0x48, OpData.ADDR_IMP, 1, 3)

        // PHP:
        setOp(OpData.INS_PHP, 0x08, OpData.ADDR_IMP, 1, 3)

        // PLA:
        setOp(OpData.INS_PLA, 0x68, OpData.ADDR_IMP, 1, 4)

        // PLP:
        setOp(OpData.INS_PLP, 0x28, OpData.ADDR_IMP, 1, 4)

        // ROL:
        setOp(OpData.INS_ROL, 0x2A, OpData.ADDR_ACC, 1, 2)
        setOp(OpData.INS_ROL, 0x26, OpData.ADDR_ZP, 2, 5)
        setOp(OpData.INS_ROL, 0x36, OpData.ADDR_ZPX, 2, 6)
        setOp(OpData.INS_ROL, 0x2E, OpData.ADDR_ABS, 3, 6)
        setOp(OpData.INS_ROL, 0x3E, OpData.ADDR_ABSX, 3, 7)

        // ROR:
        setOp(OpData.INS_ROR, 0x6A, OpData.ADDR_ACC, 1, 2)
        setOp(OpData.INS_ROR, 0x66, OpData.ADDR_ZP, 2, 5)
        setOp(OpData.INS_ROR, 0x76, OpData.ADDR_ZPX, 2, 6)
        setOp(OpData.INS_ROR, 0x6E, OpData.ADDR_ABS, 3, 6)
        setOp(OpData.INS_ROR, 0x7E, OpData.ADDR_ABSX, 3, 7)

        // RTI:
        setOp(OpData.INS_RTI, 0x40, OpData.ADDR_IMP, 1, 6)

        // RTS:
        setOp(OpData.INS_RTS, 0x60, OpData.ADDR_IMP, 1, 6)

        // SBC:
        setOp(OpData.INS_SBC, 0xE9, OpData.ADDR_IMM, 2, 2)
        setOp(OpData.INS_SBC, 0xE5, OpData.ADDR_ZP, 2, 3)
        setOp(OpData.INS_SBC, 0xF5, OpData.ADDR_ZPX, 2, 4)
        setOp(OpData.INS_SBC, 0xED, OpData.ADDR_ABS, 3, 4)
        setOp(OpData.INS_SBC, 0xFD, OpData.ADDR_ABSX, 3, 4)
        setOp(OpData.INS_SBC, 0xF9, OpData.ADDR_ABSY, 3, 4)
        setOp(OpData.INS_SBC, 0xE1, OpData.ADDR_PREIDXIND, 2, 6)
        setOp(OpData.INS_SBC, 0xF1, OpData.ADDR_POSTIDXIND, 2, 5)

        // SEC:
        setOp(OpData.INS_SEC, 0x38, OpData.ADDR_IMP, 1, 2)

        // SED:
        setOp(OpData.INS_SED, 0xF8, OpData.ADDR_IMP, 1, 2)

        // SEI:
        setOp(OpData.INS_SEI, 0x78, OpData.ADDR_IMP, 1, 2)

        // STA:
        setOp(OpData.INS_STA, 0x85, OpData.ADDR_ZP, 2, 3)
        setOp(OpData.INS_STA, 0x95, OpData.ADDR_ZPX, 2, 4)
        setOp(OpData.INS_STA, 0x8D, OpData.ADDR_ABS, 3, 4)
        setOp(OpData.INS_STA, 0x9D, OpData.ADDR_ABSX, 3, 5)
        setOp(OpData.INS_STA, 0x99, OpData.ADDR_ABSY, 3, 5)
        setOp(OpData.INS_STA, 0x81, OpData.ADDR_PREIDXIND, 2, 6)
        setOp(OpData.INS_STA, 0x91, OpData.ADDR_POSTIDXIND, 2, 6)

        // STX:
        setOp(OpData.INS_STX, 0x86, OpData.ADDR_ZP, 2, 3)
        setOp(OpData.INS_STX, 0x96, OpData.ADDR_ZPY, 2, 4)
        setOp(OpData.INS_STX, 0x8E, OpData.ADDR_ABS, 3, 4)

        // STY:
        setOp(OpData.INS_STY, 0x84, OpData.ADDR_ZP, 2, 3)
        setOp(OpData.INS_STY, 0x94, OpData.ADDR_ZPX, 2, 4)
        setOp(OpData.INS_STY, 0x8C, OpData.ADDR_ABS, 3, 4)

        // TAX:
        setOp(OpData.INS_TAX, 0xAA, OpData.ADDR_IMP, 1, 2)

        // TAY:
        setOp(OpData.INS_TAY, 0xA8, OpData.ADDR_IMP, 1, 2)

        // TSX:
        setOp(OpData.INS_TSX, 0xBA, OpData.ADDR_IMP, 1, 2)

        // TXA:
        setOp(OpData.INS_TXA, 0x8A, OpData.ADDR_IMP, 1, 2)

        // TXS:
        setOp(OpData.INS_TXS, 0x9A, OpData.ADDR_IMP, 1, 2)

        // TYA:
        setOp(OpData.INS_TYA, 0x98, OpData.ADDR_IMP, 1, 2)

        // Instruction Names:
        instname[0] = "ADC"
        instname[1] = "AND"
        instname[2] = "ASL"
        instname[3] = "BCC"
        instname[4] = "BCS"
        instname[5] = "BEQ"
        instname[6] = "BIT"
        instname[7] = "BMI"
        instname[8] = "BNE"
        instname[9] = "BPL"
        instname[10] = "BRK"
        instname[11] = "BVC"
        instname[12] = "BVS"
        instname[13] = "CLC"
        instname[14] = "CLD"
        instname[15] = "CLI"
        instname[16] = "CLV"
        instname[17] = "CMP"
        instname[18] = "CPX"
        instname[19] = "CPY"
        instname[20] = "DEC"
        instname[21] = "DEX"
        instname[22] = "DEY"
        instname[23] = "EOR"
        instname[24] = "INC"
        instname[25] = "INX"
        instname[26] = "INY"
        instname[27] = "JMP"
        instname[28] = "JSR"
        instname[29] = "LDA"
        instname[30] = "LDX"
        instname[31] = "LDY"
        instname[32] = "LSR"
        instname[33] = "NOP"
        instname[34] = "ORA"
        instname[35] = "PHA"
        instname[36] = "PHP"
        instname[37] = "PLA"
        instname[38] = "PLP"
        instname[39] = "ROL"
        instname[40] = "ROR"
        instname[41] = "RTI"
        instname[42] = "RTS"
        instname[43] = "SBC"
        instname[44] = "SEC"
        instname[45] = "SED"
        instname[46] = "SEI"
        instname[47] = "STA"
        instname[48] = "STX"
        instname[49] = "STY"
        instname[50] = "TAX"
        instname[51] = "TAY"
        instname[52] = "TSX"
        instname[53] = "TXA"
        instname[54] = "TXS"
        instname[55] = "TYA"
    }


    fun setOp(inst: Int, op: Int, addr: Int, size: Int, cycles: Int) {
        opdata[op] =
            ((inst and 0xFF)) or
        ((addr and 0xFF) shl 8) or
        ((size and 0xFF) shl 16) or
        ((cycles and 0xFF) shl 24)
    }

    fun getOp(inst: Int) = opdata[inst]
}