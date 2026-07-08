package iuh.fit.se.nextalk_be.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateThemeRequest {
    private String themeColor;
    private String wallpaperUrl;
}
