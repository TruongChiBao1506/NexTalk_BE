package iuh.fit.se.nextalk_be.sticker;

import iuh.fit.se.nextalk_be.sticker.dto.AddStickersRequest;
import iuh.fit.se.nextalk_be.sticker.dto.StickerPackRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StickerService {

    private final StickerPackRepository packRepository;
    private final StickerRepository stickerRepository;

    public List<StickerPack> getAllPacks() {
        List<StickerPack> packs = packRepository.findAll();
        for (StickerPack pack : packs) {
            pack.setStickers(stickerRepository.findByPackId(pack.getId()));
        }
        return packs;
    }

    public StickerPack createPack(StickerPackRequest request) {
        StickerPack pack = StickerPack.builder()
                .name(request.getName())
                .coverUrl(request.getCoverUrl())
                .isActive(true)
                .build();
        pack = packRepository.save(pack);
        pack.setStickers(new ArrayList<>());
        return pack;
    }

    public void addStickers(String packId, AddStickersRequest request) {
        if (!packRepository.existsById(packId)) {
            throw new RuntimeException("Pack not found");
        }
        
        List<Sticker> newStickers = request.getStickerUrls().stream()
                .map(url -> Sticker.builder()
                        .packId(packId)
                        .stickerUrl(url)
                        .isActive(true)
                        .build())
                .toList();
                
        stickerRepository.saveAll(newStickers);
    }

    public void togglePack(String packId, Boolean isActive) {
        StickerPack pack = packRepository.findById(packId)
                .orElseThrow(() -> new RuntimeException("Pack not found"));
        pack.setActive(isActive);
        packRepository.save(pack);
    }

    public void toggleSticker(String packId, String stickerId, Boolean isActive) {
        Sticker sticker = stickerRepository.findById(stickerId)
                .orElseThrow(() -> new RuntimeException("Sticker not found"));
        if (!sticker.getPackId().equals(packId)) {
            throw new RuntimeException("Sticker does not belong to pack");
        }
        sticker.setActive(isActive);
        stickerRepository.save(sticker);
    }

    public void deleteSticker(String packId, String stickerId) {
        Sticker sticker = stickerRepository.findById(stickerId)
                .orElseThrow(() -> new RuntimeException("Sticker not found"));
        if (!sticker.getPackId().equals(packId)) {
            throw new RuntimeException("Sticker does not belong to pack");
        }
        stickerRepository.delete(sticker);
    }
}
