<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="head.jspf" %>
<p class="demo-note">데모 예시 전용 · 모든 금액·산식·위험점수는 교육용 가정입니다. 실제 재무자료를 입력하지 마세요. 회계기준·규제 준수 판정이나 투자 조언을 제공하지 않습니다.</p>
<div class="report-kpis"><c:forEach items="${reportCounts}" var="count"><div><small>열람 가능한 <c:out value="${labels[count.key]}"/></small><strong>${count.value}건</strong></div></c:forEach></div><section class="panel"><h2>내 검토 대기 (오래된 순 최대 10건)</h2><c:forEach items="${reviewQueue}" var="r"><a class="feed" href="${ctx}/reports/${r.report_code}/${r.id}"><strong><c:out value="${r.title}"/></strong><span>기준일 <c:out value="${r.period_end}"/> →</span></a></c:forEach><c:if test="${empty reviewQueue}"><p class="muted">현재 지정된 검토 대기 보고서가 없습니다.</p></c:if></section>
<section class="panel"><h2>작성 → 검토 → 승인, 이력을 남기는 보고 업무</h2><p>각 메뉴에서 기간·통화·관리단위와 최대 12개 명세를 작성합니다. 작성자와 지정 검토자, 관리자만 조회합니다. 승인본은 수정·삭제할 수 없으며 사본으로 개정합니다. 삭제한 초안은 휴지통에서 복구할 수 있습니다.</p><a class="text-link" href="${ctx}/profile">내 계정 확인</a></section>
<c:forEach items="${reportGroups}" var="group"><section class="report-section"><h2><c:out value="${group.key}"/> <span class="badge">5개 업무</span></h2><div class="report-cards"><c:forEach items="${group.value}" var="item"><a class="panel menu-card" href="${ctx}/reports/${item.code}"><h3><c:out value="${item.title}"/></h3><p><c:out value="${item.purpose}"/></p><small><c:out value="${item.dimension}"/>별 명세 · 작성/조회/수정/복구 →</small></a></c:forEach></div></section></c:forEach>
<%@ include file="foot.jspf" %>
