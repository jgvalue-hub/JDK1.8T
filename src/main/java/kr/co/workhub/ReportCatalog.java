package kr.co.workhub;

import java.util.*;

/**
 * Fixed business catalog: exactly 50 main entries, ten genuinely different input/calculation
 * families.
 */
public final class ReportCatalog {
  public static final class Type {
    final String family, group, code, title, dimension, purpose, formula;
    final List<String> columns, categories;

    Type(
        String family,
        String group,
        String code,
        String title,
        String dimension,
        String purpose,
        String formula,
        String[] columns,
        String categories) {
      this.family = family;
      this.group = group;
      this.code = code;
      this.title = title;
      this.dimension = dimension;
      this.purpose = purpose;
      this.formula = formula;
      this.columns = Collections.unmodifiableList(Arrays.asList(columns));
      this.categories = Collections.unmodifiableList(Arrays.asList(categories.split(",")));
    }

    public String getCode() {
      return code;
    }

    public String getTitle() {
      return title;
    }

    public String getFamily() {
      return family;
    }

    public String getGroup() {
      return group;
    }

    public String getDimension() {
      return dimension;
    }

    public String getPurpose() {
      return purpose;
    }

    public String getFormula() {
      return formula;
    }

    public List<String> getColumns() {
      return columns;
    }

    public List<String> getCategories() {
      return categories;
    }
  }

  private static final Map<String, Type> TYPES = new LinkedHashMap<>();

  static {
    add(
        new Type(
            "LEDGER",
            "결산·원장",
            "journal-vouchers",
            "분개전표 보고서",
            "계정과목",
            "거래별 차대변 명세와 조정 근거를 검토합니다.",
            "차변 − 대변 + 조정. 전표·시산표 상신 시 합계 0 필요.",
            new String[] {"차변 금액", "대변 금액", "조정 금액"},
            "현금,매출,비용"));
    add(
        new Type(
            "LEDGER",
            "결산·원장",
            "trial-balance",
            "합계잔액시산표",
            "계정과목",
            "계정별 잔액과 차대변 균형을 확인합니다.",
            "차변 − 대변 + 조정. 전표·시산표 상신 시 합계 0 필요.",
            new String[] {"차변 금액", "대변 금액", "조정 금액"},
            "자산,부채,자본"));
    add(
        new Type(
            "LEDGER",
            "결산·원장",
            "closing-adjustments",
            "결산조정 보고서",
            "조정항목",
            "기간 귀속 조정과 결산 근거를 관리합니다.",
            "차변 − 대변 + 조정. 전표·시산표 상신 시 합계 0 필요.",
            new String[] {"차변 금액", "대변 금액", "조정 금액"},
            "미수,미지급,선급"));
    add(
        new Type(
            "LEDGER",
            "결산·원장",
            "expense-ledger",
            "비용원장 분석",
            "비용항목",
            "비용별 전기·상계·조정 내역을 분석합니다.",
            "차변 − 대변 + 조정. 전표·시산표 상신 시 합계 0 필요.",
            new String[] {"차변 금액", "대변 금액", "조정 금액"},
            "인건비,임차료,용역비"));
    add(
        new Type(
            "LEDGER",
            "결산·원장",
            "intercompany",
            "관계사 대사 보고서",
            "관계사",
            "관계사 채권·채무 및 대사 조정을 기록합니다.",
            "차변 − 대변 + 조정. 전표·시산표 상신 시 합계 0 필요.",
            new String[] {"차변 금액", "대변 금액", "조정 금액"},
            "본사,자회사,해외법인"));
    add(
        new Type(
            "RECEIVABLE",
            "매출·채권",
            "receivable-aging",
            "매출채권 연령분석",
            "거래처",
            "거래처별 회수잔액과 연체 구간을 분석합니다.",
            "미회수 = 청구 − 회수. 90일 이상 연체 명세를 표시합니다.",
            new String[] {"청구 금액", "회수 금액", "연체 일수"},
            "국내,해외,관계사"));
    add(
        new Type(
            "RECEIVABLE",
            "매출·채권",
            "collection-plan",
            "채권회수 계획",
            "회수대상",
            "회수 실적과 미회수 잔액을 관리합니다.",
            "미회수 = 청구 − 회수. 90일 이상 연체 명세를 표시합니다.",
            new String[] {"청구 금액", "회수 금액", "연체 일수"},
            "정상,협의,분쟁"));
    add(
        new Type(
            "RECEIVABLE",
            "매출·채권",
            "sales-reconciliation",
            "매출 정산대사",
            "정산처",
            "매출 청구와 실제 정산 차이를 대사합니다.",
            "미회수 = 청구 − 회수. 90일 이상 연체 명세를 표시합니다.",
            new String[] {"청구 금액", "회수 금액", "연체 일수"},
            "온라인,오프라인,대리점"));
    add(
        new Type(
            "RECEIVABLE",
            "매출·채권",
            "customer-arrears",
            "거래처 연체현황",
            "거래처",
            "거래처 중요도별 연체 현황을 점검합니다.",
            "미회수 = 청구 − 회수. 90일 이상 연체 명세를 표시합니다.",
            new String[] {"청구 금액", "회수 금액", "연체 일수"},
            "핵심,일반,신규"));
    add(
        new Type(
            "RECEIVABLE",
            "매출·채권",
            "revenue-cutoff",
            "매출 기간귀속 검토",
            "계약명",
            "기간말 청구·회수와 증빙 메모를 검토합니다.",
            "미회수 = 청구 − 회수. 90일 이상 연체 명세를 표시합니다.",
            new String[] {"청구 금액", "회수 금액", "연체 일수"},
            "납품,용역,구독"));
    add(
        new Type(
            "PAYABLE",
            "매입·채무",
            "payable-aging",
            "매입채무 잔액보고",
            "공급업체",
            "공급업체별 지급 및 공제 잔액을 관리합니다.",
            "미지급 = 매입 − 지급 − 공제. 지급·공제는 매입을 초과할 수 없습니다.",
            new String[] {"매입 금액", "지급 금액", "할인·공제"},
            "원재료,상품,용역"));
    add(
        new Type(
            "PAYABLE",
            "매입·채무",
            "payment-plan",
            "자금 지급계획",
            "지급대상",
            "지급 예정 잔액과 우선순위를 검토합니다.",
            "미지급 = 매입 − 지급 − 공제. 지급·공제는 매입을 초과할 수 없습니다.",
            new String[] {"매입 금액", "지급 금액", "할인·공제"},
            "정기,수시,긴급"));
    add(
        new Type(
            "PAYABLE",
            "매입·채무",
            "vendor-reconciliation",
            "공급업체 정산대사",
            "공급업체",
            "매입 원장과 지급명세의 차이를 대사합니다.",
            "미지급 = 매입 − 지급 − 공제. 지급·공제는 매입을 초과할 수 없습니다.",
            new String[] {"매입 금액", "지급 금액", "할인·공제"},
            "국내,수입,관계사"));
    add(
        new Type(
            "PAYABLE",
            "매입·채무",
            "purchase-accrual",
            "미청구 매입검토",
            "발주명",
            "매입 인식과 미지급 추정 근거를 보관합니다.",
            "미지급 = 매입 − 지급 − 공제. 지급·공제는 매입을 초과할 수 없습니다.",
            new String[] {"매입 금액", "지급 금액", "할인·공제"},
            "입고,검수,정산"));
    add(
        new Type(
            "PAYABLE",
            "매입·채무",
            "expense-settlement",
            "경비 정산보고",
            "정산대상",
            "경비 청구·지급·공제 내역을 검토합니다.",
            "미지급 = 매입 − 지급 − 공제. 지급·공제는 매입을 초과할 수 없습니다.",
            new String[] {"매입 금액", "지급 금액", "할인·공제"},
            "출장,법인카드,기타"));
    add(
        new Type(
            "ASSET",
            "자산·투자",
            "fixed-assets",
            "고정자산 명세",
            "자산명",
            "자산 취득원가와 상각 가정을 관리합니다.",
            "정액 월상각 예시 = (원가 − 잔존가치) ÷ 개월. 세무·회계기준 판정 아님.",
            new String[] {"취득 원가", "잔존 가치", "내용연수(개월)"},
            "건물,설비,비품"));
    add(
        new Type(
            "ASSET",
            "자산·투자",
            "depreciation",
            "감가상각 시뮬레이션",
            "자산명",
            "가정별 월상각액을 비교합니다.",
            "정액 월상각 예시 = (원가 − 잔존가치) ÷ 개월. 세무·회계기준 판정 아님.",
            new String[] {"취득 원가", "잔존 가치", "내용연수(개월)"},
            "정액,검토,보류"));
    add(
        new Type(
            "ASSET",
            "자산·투자",
            "asset-disposal",
            "자산 처분검토",
            "처분대상",
            "처분 검토 대상의 원가·잔존가치 근거를 기록합니다.",
            "정액 월상각 예시 = (원가 − 잔존가치) ÷ 개월. 세무·회계기준 판정 아님.",
            new String[] {"취득 원가", "잔존 가치", "내용연수(개월)"},
            "매각,폐기,이관"));
    add(
        new Type(
            "ASSET",
            "자산·투자",
            "capex-review",
            "설비투자 검토",
            "투자안",
            "설비투자 원가와 사용기간 가정을 검토합니다.",
            "정액 월상각 예시 = (원가 − 잔존가치) ÷ 개월. 세무·회계기준 판정 아님.",
            new String[] {"취득 원가", "잔존 가치", "내용연수(개월)"},
            "증설,교체,신규"));
    add(
        new Type(
            "ASSET",
            "자산·투자",
            "intangible-assets",
            "무형자산 검토",
            "자산명",
            "무형자산의 단순 월상각 예시를 검토합니다.",
            "정액 월상각 예시 = (원가 − 잔존가치) ÷ 개월. 세무·회계기준 판정 아님.",
            new String[] {"취득 원가", "잔존 가치", "내용연수(개월)"},
            "소프트웨어,특허,개발비"));
    add(
        new Type(
            "BUDGET",
            "예산·손익",
            "budget-performance",
            "예산 대비 실적",
            "관리항목",
            "예산·실적·전망과 잔여 예산을 분석합니다.",
            "잔여 예산 = 예산 − 실적. 전망은 별도 합계로 표시합니다.",
            new String[] {"예산 금액", "실적 금액", "전망 금액"},
            "매출,비용,투자"));
    add(
        new Type(
            "BUDGET",
            "예산·손익",
            "department-budget",
            "부서별 예산보고",
            "부서명",
            "부서별 집행과 전망을 관리합니다.",
            "잔여 예산 = 예산 − 실적. 전망은 별도 합계로 표시합니다.",
            new String[] {"예산 금액", "실적 금액", "전망 금액"},
            "영업,개발,지원"));
    add(
        new Type(
            "BUDGET",
            "예산·손익",
            "project-profit",
            "프로젝트 손익계획",
            "프로젝트",
            "프로젝트 항목별 목표와 실적 차이를 분석합니다.",
            "잔여 예산 = 예산 − 실적. 전망은 별도 합계로 표시합니다.",
            new String[] {"예산 금액", "실적 금액", "전망 금액"},
            "수익,원가,간접비"));
    add(
        new Type(
            "BUDGET",
            "예산·손익",
            "cost-variance",
            "원가 차이분석",
            "원가요소",
            "원가 예산 초과 항목과 원인을 기록합니다.",
            "잔여 예산 = 예산 − 실적. 전망은 별도 합계로 표시합니다.",
            new String[] {"예산 금액", "실적 금액", "전망 금액"},
            "재료비,노무비,경비"));
    add(
        new Type(
            "BUDGET",
            "예산·손익",
            "rolling-forecast",
            "롤링 전망보고",
            "전망항목",
            "여러 시나리오의 예산·실적·전망을 비교합니다.",
            "잔여 예산 = 예산 − 실적. 전망은 별도 합계로 표시합니다.",
            new String[] {"예산 금액", "실적 금액", "전망 금액"},
            "기준,낙관,보수"));
    add(
        new Type(
            "CASH",
            "자금·유동성",
            "cash-position",
            "일일 자금현황",
            "계좌명",
            "계좌별 자금 유입·유출과 잔액을 점검합니다.",
            "기말 잔액 = 기초 + 유입 − 유출. 음수는 자금부족 예시입니다.",
            new String[] {"기초 잔액", "유입 금액", "유출 금액"},
            "운영,예치,외화"));
    add(
        new Type(
            "CASH",
            "자금·유동성",
            "cash-flow",
            "현금흐름 계획",
            "현금흐름 항목",
            "활동별 자금흐름을 계획합니다.",
            "기말 잔액 = 기초 + 유입 − 유출. 음수는 자금부족 예시입니다.",
            new String[] {"기초 잔액", "유입 금액", "유출 금액"},
            "영업,투자,재무"));
    add(
        new Type(
            "CASH",
            "자금·유동성",
            "liquidity-gap",
            "유동성 갭 분석",
            "기간구간",
            "기간구간별 부족·잉여 자금을 분석합니다.",
            "기말 잔액 = 기초 + 유입 − 유출. 음수는 자금부족 예시입니다.",
            new String[] {"기초 잔액", "유입 금액", "유출 금액"},
            "단기,중기,장기"));
    add(
        new Type(
            "CASH",
            "자금·유동성",
            "funding-plan",
            "차입·조달 계획",
            "조달원",
            "조달 가정에 따른 자금 잔액을 검토합니다.",
            "기말 잔액 = 기초 + 유입 − 유출. 음수는 자금부족 예시입니다.",
            new String[] {"기초 잔액", "유입 금액", "유출 금액"},
            "은행,회사채,내부"));
    add(
        new Type(
            "CASH",
            "자금·유동성",
            "liquidity-stress",
            "유동성 스트레스",
            "시나리오",
            "유입 감소·유출 증가 시나리오를 비교합니다.",
            "기말 잔액 = 기초 + 유입 − 유출. 음수는 자금부족 예시입니다.",
            new String[] {"기초 잔액", "유입 금액", "유출 금액"},
            "기준,위기,극심"));
    add(
        new Type(
            "MARKET",
            "시장위험",
            "fx-risk",
            "환율 민감도 보고",
            "통화포지션",
            "환율 충격과 헤지 가정의 손실 예시입니다.",
            "손실 예시 = 노출 × 충격률 ÷ 100 × (1 − 헤지율 ÷ 100). VaR 아님.",
            new String[] {"노출 금액", "충격률(%)", "헤지율(%)"},
            "USD,EUR,JPY"));
    add(
        new Type(
            "MARKET",
            "시장위험",
            "interest-risk",
            "금리 민감도 보고",
            "금리포지션",
            "금리 충격의 단순 노출 손실을 비교합니다.",
            "손실 예시 = 노출 × 충격률 ÷ 100 × (1 − 헤지율 ÷ 100). VaR 아님.",
            new String[] {"노출 금액", "충격률(%)", "헤지율(%)"},
            "고정,변동,혼합"));
    add(
        new Type(
            "MARKET",
            "시장위험",
            "price-risk",
            "가격변동 위험",
            "자산·상품",
            "가격 하락 가정과 방어 비중을 검토합니다.",
            "손실 예시 = 노출 × 충격률 ÷ 100 × (1 − 헤지율 ÷ 100). VaR 아님.",
            new String[] {"노출 금액", "충격률(%)", "헤지율(%)"},
            "주식,원자재,기타"));
    add(
        new Type(
            "MARKET",
            "시장위험",
            "hedge-effect",
            "헤지 시나리오 비교",
            "헤지전략",
            "헤지 비중별 잔여 손실을 비교합니다.",
            "손실 예시 = 노출 × 충격률 ÷ 100 × (1 − 헤지율 ÷ 100). VaR 아님.",
            new String[] {"노출 금액", "충격률(%)", "헤지율(%)"},
            "미헤지,부분헤지,완전헤지"));
    add(
        new Type(
            "MARKET",
            "시장위험",
            "market-stress",
            "시장 스트레스 보고",
            "시장시나리오",
            "공통 충격률 가정을 사용한 예시 보고서입니다.",
            "손실 예시 = 노출 × 충격률 ÷ 100 × (1 − 헤지율 ÷ 100). VaR 아님.",
            new String[] {"노출 금액", "충격률(%)", "헤지율(%)"},
            "기준,위기,극심"));
    add(
        new Type(
            "CREDIT",
            "신용위험",
            "credit-exposure",
            "거래처 신용노출",
            "거래처",
            "거래처별 신용노출과 손실 가정을 관리합니다.",
            "기대손실 예시 = 노출 × 부도확률 × 손실률 ÷ 10000. 규제 ECL 산출 아님.",
            new String[] {"신용 노출", "부도확률(%)", "손실률(%)"},
            "우량,일반,주의"));
    add(
        new Type(
            "CREDIT",
            "신용위험",
            "expected-loss",
            "기대손실 시뮬레이션",
            "포트폴리오",
            "단순 확률 기반 손실을 계산합니다.",
            "기대손실 예시 = 노출 × 부도확률 × 손실률 ÷ 10000. 규제 ECL 산출 아님.",
            new String[] {"신용 노출", "부도확률(%)", "손실률(%)"},
            "정상,관찰,부실"));
    add(
        new Type(
            "CREDIT",
            "신용위험",
            "credit-concentration",
            "신용 집중도 검토",
            "집중구간",
            "구간별 노출 집중과 손실 가정을 검토합니다.",
            "기대손실 예시 = 노출 × 부도확률 × 손실률 ÷ 10000. 규제 ECL 산출 아님.",
            new String[] {"신용 노출", "부도확률(%)", "손실률(%)"},
            "업종,지역,그룹"));
    add(
        new Type(
            "CREDIT",
            "신용위험",
            "collateral-review",
            "담보 회수위험",
            "담보대상",
            "담보 회수 가정을 손실률에 반영해 비교합니다.",
            "기대손실 예시 = 노출 × 부도확률 × 손실률 ÷ 10000. 규제 ECL 산출 아님.",
            new String[] {"신용 노출", "부도확률(%)", "손실률(%)"},
            "부동산,보증,무담보"));
    add(
        new Type(
            "CREDIT",
            "신용위험",
            "counterparty-watch",
            "거래상대방 관찰",
            "상대방",
            "관찰대상과 손실 가정 변경 근거를 기록합니다.",
            "기대손실 예시 = 노출 × 부도확률 × 손실률 ÷ 10000. 규제 ECL 산출 아님.",
            new String[] {"신용 노출", "부도확률(%)", "손실률(%)"},
            "정상,관찰,경보"));
    add(
        new Type(
            "OPERATION",
            "운영위험",
            "risk-register",
            "운영위험 등록부",
            "위험사건",
            "위험사건의 고유·잔여위험을 평가합니다.",
            "잔여점수 = 가능성 × 영향도 × (1 − 통제효과 ÷ 100). 최고점수로 요약합니다.",
            new String[] {"발생가능성(1~5)", "영향도(1~5)", "통제효과(%)"},
            "인력,프로세스,시스템"));
    add(
        new Type(
            "OPERATION",
            "운영위험",
            "incident-review",
            "사고 영향평가",
            "사고명",
            "사고 재발 가능성과 통제효과를 검토합니다.",
            "잔여점수 = 가능성 × 영향도 × (1 − 통제효과 ÷ 100). 최고점수로 요약합니다.",
            new String[] {"발생가능성(1~5)", "영향도(1~5)", "통제효과(%)"},
            "오류,중단,부정"));
    add(
        new Type(
            "OPERATION",
            "운영위험",
            "business-continuity",
            "업무연속성 위험",
            "핵심업무",
            "업무중단 시나리오의 잔여점수를 관리합니다.",
            "잔여점수 = 가능성 × 영향도 × (1 − 통제효과 ÷ 100). 최고점수로 요약합니다.",
            new String[] {"발생가능성(1~5)", "영향도(1~5)", "통제효과(%)"},
            "결제,보고,서비스"));
    add(
        new Type(
            "OPERATION",
            "운영위험",
            "vendor-risk",
            "외주·공급망 위험",
            "외주업체",
            "외부 의존 위험과 통제효과를 평가합니다.",
            "잔여점수 = 가능성 × 영향도 × (1 − 통제효과 ÷ 100). 최고점수로 요약합니다.",
            new String[] {"발생가능성(1~5)", "영향도(1~5)", "통제효과(%)"},
            "서비스,공급,위탁"));
    add(
        new Type(
            "OPERATION",
            "운영위험",
            "security-risk",
            "정보보호 위험평가",
            "정보자산",
            "정보자산별 예시 위험점수를 관리합니다.",
            "잔여점수 = 가능성 × 영향도 × (1 − 통제효과 ÷ 100). 최고점수로 요약합니다.",
            new String[] {"발생가능성(1~5)", "영향도(1~5)", "통제효과(%)"},
            "접근,유출,가용성"));
    add(
        new Type(
            "CONTROL",
            "통제·보고품질",
            "control-testing",
            "내부통제 점검",
            "통제명",
            "통제별 점검 완료와 예외를 추적합니다.",
            "완료율 = 완료 ÷ 대상 × 100. 예외는 완료 건수 이내여야 합니다.",
            new String[] {"점검 대상 수", "완료 건수", "예외 건수"},
            "예방,탐지,시정"));
    add(
        new Type(
            "CONTROL",
            "통제·보고품질",
            "audit-actions",
            "감사 개선조치",
            "개선과제",
            "감사 개선과제의 이행·예외 현황을 관리합니다.",
            "완료율 = 완료 ÷ 대상 × 100. 예외는 완료 건수 이내여야 합니다.",
            new String[] {"점검 대상 수", "완료 건수", "예외 건수"},
            "설계,운영,증빙"));
    add(
        new Type(
            "CONTROL",
            "통제·보고품질",
            "report-quality",
            "보고서 품질점검",
            "검증항목",
            "보고 품질 점검 실적과 오류를 기록합니다.",
            "완료율 = 완료 ÷ 대상 × 100. 예외는 완료 건수 이내여야 합니다.",
            new String[] {"점검 대상 수", "완료 건수", "예외 건수"},
            "정합성,완전성,적시성"));
    add(
        new Type(
            "CONTROL",
            "통제·보고품질",
            "compliance-check",
            "준수사항 자체점검",
            "체크항목",
            "내부 체크리스트 예시이며 법규 준수 판정이 아닙니다.",
            "완료율 = 완료 ÷ 대상 × 100. 예외는 완료 건수 이내여야 합니다.",
            new String[] {"점검 대상 수", "완료 건수", "예외 건수"},
            "내규,계약,절차"));
    add(
        new Type(
            "CONTROL",
            "통제·보고품질",
            "risk-actions",
            "위험 대응추적",
            "대응과제",
            "위험 대응과제의 이행과 미해결 예외를 추적합니다.",
            "완료율 = 완료 ÷ 대상 × 100. 예외는 완료 건수 이내여야 합니다.",
            new String[] {"점검 대상 수", "완료 건수", "예외 건수"},
            "회피,완화,이전"));
    if (TYPES.size() != 50)
      throw new IllegalStateException("Business menu catalog must contain 50 entries");
  }

  private static void add(Type t) {
    if (TYPES.put(t.code, t) != null) throw new IllegalStateException("Duplicate report code");
  }

  public static Type get(String code) {
    Type t = TYPES.get(code);
    if (t == null) throw new Problem(404, "보고서 메뉴를 찾을 수 없습니다.");
    return t;
  }

  public static Collection<Type> all() {
    return Collections.unmodifiableCollection(TYPES.values());
  }

  public static Map<String, List<Type>> groups() {
    Map<String, List<Type>> result = new LinkedHashMap<>();
    for (Type t : all()) result.computeIfAbsent(t.group, k -> new ArrayList<>()).add(t);
    return result;
  }

  private ReportCatalog() {}
}
