package com.chamabosal.domain.store.controller;

import com.chamabosal.domain.store.dto.request.EquippedItemsUpdateRequest;
import com.chamabosal.domain.store.dto.response.ItemPurchaseResponse;
import com.chamabosal.domain.store.dto.response.StoreResponse;
import com.chamabosal.domain.store.service.StoreService;
import com.chamabosal.global.common.response.ErrorResponse;
import com.chamabosal.global.common.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "[STORE] 곳간 API", description = "곳간 조회, 꾸미기 아이템 구매·착용")
public class StoreController {

    private final StoreService storeService;

    @GetMapping("/store")
    @Operation(summary = "곳간 조회 (STORE-01)",
            description = """
                    공양미 표시 잔액, 방어권 상태, 꾸미기 아이템 목록(노출 순서)을 돌려줍니다.
                    곳간 안의 구매·착용은 itemId를 쓰고, 보살 캐릭터를 그리는 다른 응답은 code(equippedItemCodes)를 씁니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "COMMON_008 로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<StoreResponse>> getStore(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(Response.ok(storeService.getStore(userId)));
    }

    @PostMapping("/items/{itemId}/purchases")
    @Operation(summary = "아이템 구매 (STORE-02)",
            description = """
                    공양미로 꾸미기 아이템을 사고 바로 착용합니다. 같은 슬롯에 끼고 있던 아이템은 벗고 보유로 남습니다.
                    확인 순서: STORE_002 → STORE_003 → STORE_001
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "구매 성공"),
            @ApiResponse(responseCode = "404", description = "STORE_002 없는 아이템",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "STORE_003 이미 보유 / STORE_001 공양미 부족",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<ItemPurchaseResponse>> purchase(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long itemId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.created("아이템을 샀습니다.", storeService.purchase(userId, itemId)));
    }

    @PutMapping("/users/me/equipped-items")
    @Operation(summary = "착용 · 벗기 (STORE-03)",
            description = """
                    착용할 아이템 목록을 보내면 착용 상태를 통째로 바꿉니다. 목록에 없는 아이템은 벗고, 빈 배열이면 모두 벗습니다.
                    착용은 사용자 단위라 모든 방에 같이 보입니다.
                    곳간에서 끼워 보고 벗어 보는 동안에는 보내지 말고, 마지막 착용 목록을 한 번에 보내 주세요.
                    확인 순서: COMMON_002(목록 없음·3개 초과·같은 ID 중복) → STORE_004(없거나 보유하지 않은 아이템) → COMMON_002(같은 슬롯 둘 이상)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "착용 상태 저장"),
            @ApiResponse(responseCode = "400", description = "COMMON_002 입력값 오류 (field: itemIds)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "STORE_004 보유하지 않은 아이템",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Response<Void>> equip(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestBody @Valid EquippedItemsUpdateRequest request
    ) {
        storeService.equip(userId, request);
        return ResponseEntity.ok(Response.ok(null));
    }
}
