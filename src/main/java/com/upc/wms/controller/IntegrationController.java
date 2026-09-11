package com.upc.wms.controller;

import com.upc.wms.common.Result;
import com.upc.wms.entity.IntIntegrationMessage;
import com.upc.wms.entity.IntSystem;
import com.upc.wms.service.IntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/integration")
@RequiredArgsConstructor
public class IntegrationController {

    private final IntegrationService integrationService;

    @GetMapping("/system/list")
    public Result<List<IntSystem>> listSystems() {
        return Result.success(integrationService.listSystems());
    }

    @PostMapping("/system/add")
    public Result<IntSystem> addSystem(@RequestBody IntSystem system) {
        return Result.success(integrationService.addSystem(system));
    }

    @GetMapping("/message/list")
    public Result<List<IntIntegrationMessage>> listMessages() {
        return Result.success(integrationService.listPendingMessages());
    }

    @PostMapping("/message/inbound")
    public Result<IntIntegrationMessage> inbound(@RequestParam Long systemId,
                                                 @RequestParam String messageType,
                                                 @RequestParam(required = false) String businessKey,
                                                 @RequestBody String payloadJson) {
        return Result.success(integrationService.saveInboundMessage(systemId, messageType, businessKey, payloadJson));
    }

    @PostMapping("/message/{id}/success")
    public Result<Void> markSuccess(@PathVariable("id") Long id) {
        integrationService.markMessageSuccess(id);
        return Result.success();
    }

    @PostMapping("/message/{id}/failed")
    public Result<Void> markFailed(@PathVariable("id") Long id, @RequestParam String errorMessage) {
        integrationService.markMessageFailed(id, errorMessage);
        return Result.success();
    }

    @PostMapping("/message/retry")
    public Result<Void> retry(@RequestParam Long messageId) {
        integrationService.retryMessage(messageId);
        return Result.success();
    }
}
