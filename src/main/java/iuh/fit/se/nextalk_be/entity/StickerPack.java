package iuh.fit.se.nextalk_be.entity;

import iuh.fit.se.nextalk_be.entity.BaseEntity;
import iuh.fit.se.nextalk_be.entity.Sticker;


import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sticker_packs")
public class StickerPack extends BaseEntity {
    private String name;
    private String coverUrl;
    
    @Builder.Default
    @JsonProperty("isActive")
    private boolean isActive = true;
    
    @Builder.Default
    private int sortOrder = 0;
    
    @org.springframework.data.annotation.Transient
    private List<Sticker> stickers;
}
