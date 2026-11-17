package com.rubberduck.domain.document.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

@Service
public class TextExtractionService {

    public String extract(MultipartFile file) {
        try {
            String fileName = normalize(file.getOriginalFilename());
            String contentType = normalize(file.getContentType());
            byte[] bytes = file.getBytes();

            if (isPdf(fileName, contentType)) {
                return normalizeText(extractPdf(bytes));
            }
            if (isDocx(fileName, contentType)) {
                return normalizeText(extractDocx(bytes));
            }
            if (isPptx(fileName, contentType)) {
                return normalizeText(extractPptx(bytes));
            }

            return normalizeText(new String(bytes, StandardCharsets.UTF_8));
        } catch (CustomException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendLine(builder, paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    appendDocxTable(builder, table);
                }
            }
        }
        return builder.toString();
    }

    private String extractPptx(byte[] bytes) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            for (XSLFSlide slide : slideShow.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    appendPptxShape(builder, shape);
                }
            }
        }
        return builder.toString();
    }

    private void appendDocxTable(StringBuilder builder, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                appendLine(builder, cell.getText());
            }
        }
    }

    private void appendPptxShape(StringBuilder builder, XSLFShape shape) {
        if (shape instanceof XSLFTextShape textShape) {
            appendLine(builder, textShape.getText());
            return;
        }
        if (shape instanceof XSLFTable table) {
            appendPptxTable(builder, table);
            return;
        }
        if (shape instanceof XSLFGroupShape groupShape) {
            for (XSLFShape childShape : groupShape.getShapes()) {
                appendPptxShape(builder, childShape);
            }
        }
    }

    private void appendPptxTable(StringBuilder builder, XSLFTable table) {
        for (XSLFTableRow row : table.getRows()) {
            for (XSLFTableCell cell : row.getCells()) {
                appendLine(builder, cell.getText());
            }
        }
    }

    private void appendLine(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(value);
    }

    private boolean isPdf(String fileName, String contentType) {
        return fileName.endsWith(".pdf") || contentType.equals("application/pdf");
    }

    private boolean isDocx(String fileName, String contentType) {
        return fileName.endsWith(".docx") || contentType.contains("wordprocessingml.document");
    }

    private boolean isPptx(String fileName, String contentType) {
        return fileName.endsWith(".pptx") || contentType.contains("presentationml.presentation");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeText(String text) {
        String normalized = text == null ? "" : text
                .replace('\u0000', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return normalized;
    }
}
