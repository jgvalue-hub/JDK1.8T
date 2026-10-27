<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!doctype html><html lang="ko"><meta charset="UTF-8"><title>요청 확인</title><body><h1>접근 권한이 없거나 보안 토큰이 만료되었습니다.</h1><a href="${pageContext.request.contextPath}/dashboard">대시보드로 돌아가기</a></body></html>