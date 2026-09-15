package com.example.HRMS.csvimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.HRMS.csvimport.validation.CsvParser;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the RFC-4180 CSV parser (no Spring context needed). */
class CsvParserTests {

    @Test
    void parsesSimpleRow() {
        var rows = CsvParser.parse("a,b,c\n1,2,3");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly("a", "b", "c");
        assertThat(rows.get(1)).containsExactly("1", "2", "3");
    }

    @Test
    void handlesQuotedFieldWithComma() {
        var rows = CsvParser.parse("\"hello, world\",b");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(0)).isEqualTo("hello, world");
    }

    @Test
    void handlesEscapedDoubleQuote() {
        var rows = CsvParser.parse("\"a\"\"b\",c");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(0)).isEqualTo("a\"b");
    }

    @Test
    void dropsTrailingBlankLine() {
        var rows = CsvParser.parse("a,b\n1,2\n");
        assertThat(rows).hasSize(2);
    }

    @Test
    void emptyStringProducesNoRows() {
        assertThat(CsvParser.parse("")).isEmpty();
    }

    @Test
    void handlesCrlfLineEndings() {
        var rows = CsvParser.parse("a,b\r\n1,2\r\n");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly("a", "b");
    }
}
