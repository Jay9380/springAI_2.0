---
name: restock-policy
description: 상품 재발주(재주문) 수량을 정하는 사내 절차입니다. 사용자가 재고 보충, 재발주, 발주 수량을 물을 때 반드시 먼저 사용하세요.
---
# 재발주 절차

1. 대상 SKU의 현재 재고를 툴로 확인한다. 추측하지 않는다.
2. 안전재고 기준은 `reference/safety-stock.md` 파일을 Read 툴로 읽어 확인한다 (SKU마다 다르다).
   경로는 이 스킬 결과 맨 위의 "Base directory for this skill" 뒤에 `/reference/safety-stock.md`를 붙인 절대 경로다.
3. 발주 수량 = 안전재고 − 현재 재고. 0 이하이면 발주하지 않는다.
4. 계산 근거(현재 재고, 안전재고, 발주 수량)를 함께 보고한다.
