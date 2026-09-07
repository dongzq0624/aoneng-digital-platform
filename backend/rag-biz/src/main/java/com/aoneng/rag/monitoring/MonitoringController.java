package com.aoneng.rag.monitoring;

import com.aoneng.rag.common.result.Result;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/monitoring")
public class MonitoringController {
    private final MonitoringService service;
    public MonitoringController(MonitoringService service) { this.service = service; }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@AuthenticationPrincipal String username,
                                                @RequestParam(name = "from", required = false) String from,
                                                @RequestParam(name = "to", required = false) String to,
                                                @RequestParam(name = "kbId", required = false) Long kbId,
                                                @RequestParam(name = "docId", required = false) Long docId,
                                                @RequestParam(name = "conversationId", required = false) Long conversationId) {
        service.requireAdmin(username);
        return Result.ok(service.overview(from, to, kbId, docId, conversationId));
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(@AuthenticationPrincipal String username,
                                                  @RequestParam(name = "from", required = false) String from,
                                                  @RequestParam(name = "to", required = false) String to,
                                                  @RequestParam(name = "kbId", required = false) Long kbId,
                                                  @RequestParam(name = "docId", required = false) Long docId,
                                                  @RequestParam(name = "conversationId", required = false) Long conversationId) {
        service.requireAdmin(username);
        return Result.ok(service.dashboard(from, to, kbId, docId, conversationId));
    }

    @GetMapping("/file-processing")
    public Result<Map<String, Object>> fileProcessing(@AuthenticationPrincipal String username,
                                                       @RequestParam(name = "from", required = false) String from,
                                                       @RequestParam(name = "to", required = false) String to,
                                                       @RequestParam(name = "page", defaultValue = "1") int page,
                                                       @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        service.requireAdmin(username);
        return Result.ok(service.fileProcessing(from, to, page, pageSize));
    }

}
