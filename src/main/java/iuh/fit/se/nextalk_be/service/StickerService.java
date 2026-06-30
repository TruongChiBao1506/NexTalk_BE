package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.AddStickersRequest;
import iuh.fit.se.nextalk_be.dto.request.StickerPackRequest;
import iuh.fit.se.nextalk_be.entity.Sticker;
import iuh.fit.se.nextalk_be.entity.StickerPack;
import iuh.fit.se.nextalk_be.repository.StickerPackRepository;
import iuh.fit.se.nextalk_be.repository.StickerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

public interface StickerService {
    public List<StickerPack> getAllPacks();
    public StickerPack createPack(StickerPackRequest request);
    public void addStickers(String packId, AddStickersRequest request);
    public void togglePack(String packId, Boolean isActive);
    public void toggleSticker(String packId, String stickerId, Boolean isActive);
    public void deleteSticker(String packId, String stickerId);
}
