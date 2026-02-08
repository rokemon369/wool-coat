package org.example.woolcoat.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;

/**
 * 文件处理工具类（扩展Hutool，新增PDF解析）
 */
public class FileUtils {

    /**
     * 读取文件内容（支持txt/md/pdf）
     */
    public static String readFileContent(String filePath, String suffix) {
        if (StrUtil.isBlank(filePath) || !FileUtil.exist(filePath)) {
            throw new RuntimeException("文件不存在：" + filePath);
        }
        try {
            switch (suffix.toLowerCase()) {
                case "pdf":
                    return readPdfContent(filePath);
                case "txt":
                case "md":
                    return FileUtil.readString(filePath, "UTF-8");
                default:
                    throw new RuntimeException("不支持的文件格式：" + suffix);
            }
        } catch (Exception e) {
            throw new RuntimeException("读取文件失败：" + e.getMessage());
        }
    }

    /**
     * 解析PDF文本内容
     */
    private static String readPdfContent(String pdfPath) throws IOException {
        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            if (!document.isEncrypted()) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document).trim();
            } else {
                throw new RuntimeException("PDF文件已加密，无法解析");
            }
        }
    }

    /**
     * 创建目录（不存在则创建）
     */
    public static void createDirIfNotExist(String dirPath) {
        if (StrUtil.isBlank(dirPath)) {
            return;
        }
        File dir = new File(dirPath);
        if (!dir.exists()) {
            boolean success = dir.mkdirs();
            if (!success) {
                throw new RuntimeException("创建目录失败：" + dirPath);
            }
        }
    }
}
