package org.example.woolcoat;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.store.Directory;
import org.example.woolcoat.agent.function.FunctionCallService;
import org.example.woolcoat.agent.plan.Task;
import org.example.woolcoat.agent.plan.TaskPlanService;
import org.example.woolcoat.controller.AgentBasicController;
import org.example.woolcoat.controller.AgentCoreController;
import org.example.woolcoat.entity.KbDocument;
import org.example.woolcoat.mapper.KbDocumentChunkMapper;
import org.example.woolcoat.mapper.KbDocumentMapper;
import org.example.woolcoat.mapper.UserLongMemoryMapper;
import org.example.woolcoat.prompt.PromptService;
import org.example.woolcoat.service.LLMService;
import org.example.woolcoat.service.SessionService;
import org.example.woolcoat.service.document.DocumentService;
import org.example.woolcoat.service.memory.MemoryService;
import org.example.woolcoat.service.rag.RagSearchService;
import org.example.woolcoat.vo.request.LLMRequest;
import org.example.woolcoat.vo.request.TaskSubmitRequest;
import org.example.woolcoat.vo.request.ToolCallRequest;
import org.example.woolcoat.vo.response.LLMResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 综合测试类：启动完整 SpringBoot 上下文，
 * 简单验证所有 Controller 和 Service 是否能正常工作。
 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class WoolCoatApplicationTests {

    // ==== Web 层 ====
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentBasicController agentBasicController;

    @Autowired
    private AgentCoreController agentCoreController;

    // ==== Service 层（真实 Bean）====
    @Autowired
    private LLMService llmService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private MemoryService memoryService;

    @MockBean
    private RagSearchService ragSearchService;

    @Autowired
    private PromptService promptService;

    // ==== 基础依赖模拟（避免连接真实外部服务）====
    @MockBean
    private org.example.woolcoat.llm.LLMClient llmClient;

    @MockBean
    private FunctionCallService functionCallService;

    @MockBean
    private TaskPlanService taskPlanService;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private KbDocumentMapper kbDocumentMapper;

    @MockBean
    private KbDocumentChunkMapper kbDocumentChunkMapper;

    @MockBean
    private UserLongMemoryMapper userLongMemoryMapper;

    @MockBean
    private Directory luceneDirectory;

    @MockBean
    private Analyzer ikAnalyzer;

    @org.junit.jupiter.api.BeforeEach
    void setUpMocks() {
        // UserLongMemoryMapper：fuseMemoryToSystemPrompt 需要，否则 getLongTermMemory 返回 null 导致 NPE
        lenient().when(userLongMemoryMapper.selectList(any())).thenReturn(java.util.List.of());
        // RagSearchService.search 依赖真实 Lucene Directory，Mock 下会抛异常， stub 返回空列表
        lenient().when(ragSearchService.search(anyString(), anyInt(), anyString())).thenReturn(java.util.List.of());
    }

    /**
     * 基础验证：Spring 容器可以正常启动，核心 Bean 都已注入。
     */
    @Test
    void contextLoads() {
        assertThat(agentBasicController).isNotNull();
        assertThat(agentCoreController).isNotNull();
        assertThat(llmService).isNotNull();
        assertThat(sessionService).isNotNull();
        assertThat(documentService).isNotNull();
        assertThat(memoryService).isNotNull();
        assertThat(ragSearchService).isNotNull();
        assertThat(promptService).isNotNull();
    }

    /**
     * 测试基础对话接口 /agent/basic/chat
     */
    @Test
    void testAgentBasicChat() throws Exception {
        // 1. 模拟 Redis 短期记忆（返回空列表即可）
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        // 2. 模拟 LLM 返回结果
        LLMResponse llmResponse = new LLMResponse();
        llmResponse.setStatus("success");
        llmResponse.setContent("你好，这里是测试回答");
        llmResponse.setCostTime(10L);
        when(llmClient.chat(any(LLMRequest.class))).thenReturn(llmResponse);

        // 3. 调用接口
        mockMvc.perform(get("/agent/basic/chat")
                        .param("question", "今天天气怎么样？"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 测试知识库上传接口 /agent/basic/upload-document
     * 这里只验证接口能正常返回 500/200，不做真实文件上传逻辑。
     */
    @Test
    void testAgentBasicUploadDocumentMock() throws Exception {
        // 这里不直接构造 MultipartFile，简单验证接口路径存在即可（返回 4xx/5xx 也说明映射生效）
        mockMvc.perform(post("/agent/basic/upload-document"))
                .andExpect(status().is4xxClientError());
    }

    /**
     * 测试工具调用接口 /agent/core/tool-call
     */
    @Test
    void testAgentCoreToolCall() throws Exception {
        when(functionCallService.callTool(anyString(), anyString()))
                .thenReturn("工具调用成功结果");

        String jsonBody = """
                {
                  "userQuery": "帮我计算 1+2",
                  "sessionId": "test-session"
                }
                """;

        mockMvc.perform(post("/agent/core/tool-call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 测试多步任务接口 /agent/core/task-submit
     */
    @Test
    void testAgentCoreTaskSubmit() throws Exception {
        Task task = new Task();
        task.setUserQuery("帮我整理一下 Redis 知识");
        when(taskPlanService.submitTask(anyString(), anyString(), anyString())).thenReturn(task);

        String jsonBody = """
                {
                  "userQuery": "帮我整理一下 Redis 知识",
                  "sessionId": "test-session",
                  "userId": "test-user"
                }
                """;

        mockMvc.perform(post("/agent/core/task-submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 简单测试 LLMService：确认调用链到达 LLMClient。
     */
    @Test
    void testLLMServiceCall() {
        LLMRequest request = new LLMRequest();
        LLMResponse response = new LLMResponse();
        response.setStatus("success");
        response.setContent("mock response");
        when(llmClient.chat(any(LLMRequest.class))).thenReturn(response);

        LLMResponse result = llmService.callLLM(request);
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("success");
    }

    /**
     * 简单测试 DocumentService：这里只校验接口可被调用（Mapper 已被 Mock）。
     */
    @Test
    void testDocumentServiceListDocuments() {
        when(kbDocumentMapper.selectList(any())).thenReturn(java.util.List.of(new KbDocument()));
        var list = documentService.listDocuments("test-user");
        assertThat(list).isNotNull();
    }

    /**
     * 简单测试 MemoryService / RagSearchService / PromptService 等是否可用。
     * RagSearchService 已 Mock，search 返回空列表；MemoryService 需 UserLongMemoryMapper 已 stub。
     */
    @Test
    void testOtherServicesAvailable() {
        assertThat(memoryService.fuseMemoryToSystemPrompt("session-1", "user-1")).isNotNull();
        assertThat(ragSearchService.search("测试问题", 3, "user-1")).isEmpty();
        assertThat(promptService.loadPromptTemplate("plan-prompt.txt")).isNotNull();
    }
}
