package dev.ltp.control.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScriptHashTest {

    @Test
    void sha256IsLowerCaseHexOfTheUtf8Bytes() {
        // Test vector from FIPS 180-2
        assertThat(ScriptHash.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void whitespaceAndLineEndingsChangeTheHash() {
        assertThat(ScriptHash.sha256Hex("a\n")).isNotEqualTo(ScriptHash.sha256Hex("a\r\n"));
        assertThat(ScriptHash.sha256Hex("a")).isNotEqualTo(ScriptHash.sha256Hex("a "));
    }

    @Test
    void lengthCountsUtf8Bytes() {
        assertThat(ScriptHash.utf8Length("abc")).isEqualTo(3);
        assertThat(ScriptHash.utf8Length("ж")).isEqualTo(2);
        assertThat(ScriptHash.utf8Length("✓")).isEqualTo(3);
    }
}
