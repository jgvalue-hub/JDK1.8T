<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="head.jspf" %>
<section class="panel"><h2>요청을 처리하지 못했습니다.</h2><p role="alert"><c:out value="${message}"/></p><p class="muted">브라우저의 뒤로 가기로 입력 화면에 돌아가 수정하거나, 목록에서 최신 자료를 확인하세요.</p><a class="button" href="${ctx}/dashboard">대시보드</a></section>
<%@ include file="foot.jspf" %>