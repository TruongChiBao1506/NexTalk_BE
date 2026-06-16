package iuh.fit.se.nextalk_be.sticker.dto;

import lombok.Data;
import java.util.List;

@Data
public class AddStickersRequest {
    private List<String> stickerUrls;
}
