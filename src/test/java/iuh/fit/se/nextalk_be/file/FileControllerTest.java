package iuh.fit.se.nextalk_be.file;

import iuh.fit.se.nextalk_be.service.CloudinaryService;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
}
