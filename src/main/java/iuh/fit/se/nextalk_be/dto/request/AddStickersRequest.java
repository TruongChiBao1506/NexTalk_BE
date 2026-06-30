package iuh.fit.se.nextalk_be.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class AddStickersRequest {
    private List<String> stickerUrls;
}
