package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.request.ImageEditRequest;
import iuh.fit.se.nextalk_be.exception.BadRequestException;

final class ImageEditInstruction {
    private ImageEditInstruction() {
    }

    static String from(ImageEditRequest request) {
        if (request.getOperation() == null) {
            if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
                return request.getPrompt().trim();
            }
            throw new BadRequestException("Vui lòng chọn thao tác chỉnh sửa.");
        }

        return switch (request.getOperation()) {
            case REMOVE -> "Remove " + required(request.getSubject(), "Vui lòng nhập vật thể cần xóa")
                    + " and realistically fill the empty area.";
            case REPLACE -> "Replace " + required(request.getSubject(), "Vui lòng nhập vật thể cần thay")
                    + " with " + required(request.getReplacement(), "Vui lòng nhập vật thể thay thế") + ".";
            case RECOLOR -> "Recolor " + required(request.getSubject(), "Vui lòng nhập vật thể cần đổi màu")
                    + " to " + required(request.getColor(), "Vui lòng nhập màu mới") + ".";
            case BACKGROUND_REPLACE -> "Replace the background with "
                    + required(request.getPrompt(), "Vui lòng mô tả nền mới") + ".";
            case FILL -> "Extend the image to aspect ratio "
                    + required(request.getAspectRatio(), "Vui lòng chọn tỷ lệ ảnh")
                    + (request.getPrompt() == null || request.getPrompt().isBlank()
                    ? "."
                    : ", extending the scene with " + request.getPrompt().trim() + ".");
            case RESTORE -> "Restore the image by reducing noise and compression artifacts, then improve sharpness.";
        };
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }
}
