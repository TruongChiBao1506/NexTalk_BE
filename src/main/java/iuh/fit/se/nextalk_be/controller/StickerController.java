package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.AddStickersRequest;
import iuh.fit.se.nextalk_be.dto.request.StickerPackRequest;
import iuh.fit.se.nextalk_be.dto.request.ToggleRequest;
import iuh.fit.se.nextalk_be.service.StickerService;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stickers")
@RequiredArgsConstructor
public class StickerController {

    private final StickerService stickerService;

    @GetMapping("/packs")
    public ResponseEntity<?> getStickerPacks() {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", stickerService.getAllPacks()
        ));
    }

    @PostMapping("/packs")
    public ResponseEntity<?> createPack(@RequestBody StickerPackRequest request) {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", stickerService.createPack(request)
        ));
    }

    @PostMapping("/packs/{packId}/stickers")
    public ResponseEntity<?> addStickersToPack(@PathVariable String packId, @RequestBody AddStickersRequest request) {
        stickerService.addStickers(packId, request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/packs/{packId}/toggle")
    public ResponseEntity<?> togglePackActive(@PathVariable String packId, @RequestBody ToggleRequest request) {
        stickerService.togglePack(packId, request.getIsActive());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/packs/{packId}/stickers/{stickerId}/toggle")
    public ResponseEntity<?> toggleStickerActive(@PathVariable String packId, @PathVariable String stickerId, @RequestBody ToggleRequest request) {
        stickerService.toggleSticker(packId, stickerId, request.getIsActive());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/packs/{packId}/stickers/{stickerId}")
    public ResponseEntity<?> deleteSticker(@PathVariable String packId, @PathVariable String stickerId) {
        stickerService.deleteSticker(packId, stickerId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
