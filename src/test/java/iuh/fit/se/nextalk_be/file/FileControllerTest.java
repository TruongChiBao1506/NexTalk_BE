package iuh.fit.se.nextalk_be.file;

import iuh.fit.se.nextalk_be.entity.MediaAsset;
import iuh.fit.se.nextalk_be.entity.MediaAssetStatus;
import iuh.fit.se.nextalk_be.service.CloudinaryService;
import iuh.fit.se.nextalk_be.service.MediaAuthorizationService;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @MockitoBean
    private MediaAuthorizationService mediaAuthorizationService;

    @MockitoBean
    private RestTemplate restTemplate;

    @Test
    @WithMockUser
    void uploadFile_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hello.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!".getBytes()
        );

        Map<String, Object> mockResult = Map.of(
                "secure_url", "https://cloudinary.com/sec_url",
                "public_id", "my_public_id"
        );

        when(cloudinaryService.uploadFile(any())).thenReturn(mockResult);

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.url", is("https://cloudinary.com/sec_url")))
                .andExpect(jsonPath("$.data.publicId", is("my_public_id")));
    }

    @Test
    @WithMockUser
    void uploadFile_EmptyFile_BadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("File is empty")));
    }

    @Test
    @WithMockUser
    void readProtectedAsset_ProxiesFreePlanPrivateDownloadUrl() throws Exception {
        String privateUrl =
                "https://api.cloudinary.com/v1_1/demo/image/download?signature=signed";
        byte[] content = "private-image".getBytes();
        MediaAsset asset = MediaAsset.builder()
                .hash("asset-hash")
                .publicId("nextalk/assets/asset-hash")
                .resourceType("image")
                .format("png")
                .contentType(MediaType.IMAGE_PNG_VALUE)
                .status(MediaAssetStatus.CLEAN)
                .build();
        when(mediaAuthorizationService.assertCanDownloadAsset("asset-hash")).thenReturn(asset);
        when(cloudinaryService.createDownloadUrl(asset)).thenReturn(privateUrl);
        when(restTemplate.getForEntity(eq(URI.create(privateUrl)), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(content));

        mockMvc.perform(get("/api/files/content/asset-hash"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "private, no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .bytes(content));
    }
}
