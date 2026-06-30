package iuh.fit.se.nextalk_be.entity;

import iuh.fit.se.nextalk_be.entity.BaseEntity;


import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stickers")
public class Sticker extends BaseEntity {
    
    @Indexed
    private String packId;
    
    private String stickerUrl;
    
    @Builder.Default
    @JsonProperty("isActive")
    private boolean isActive = true;
    
    @Builder.Default
    private int sortOrder = 0;
}
