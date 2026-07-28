package com.yuegang.zhihui.common.web;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;


/** 翻译预期失败为稳定的 API 响应，并防止内部异常细节泄露给客户端。 */
@RestControllerAdvice    // 全局异常拦截器 1 usage
public class GlobalExceptionHandler { // 类定义

    private static final Log LOGGER = LogFactory.getLog(GlobalExceptionHandler.class); // 初始化日志记录器 no usages

    @ExceptionHandler(BusinessException.class)  // 处理自定义业务异常
    public ResponseEntity<ApiResponse<Void>> handleBusinessException( // 处理函数 no usages
                                                                      BusinessException exception, // 异常参数
                                                                      HttpServletRequest request) { // 请求参数
        var status = statusFor(exception.errorCode()); // 将业务码转为 HTTP 状态码
        var message = externalMessage(exception); // 获取对外展示的消息
        var body = ApiResponse.<Void>failure( // 构建失败响应信封
                exception.errorCode(), message, TraceIdResolver.resolve(request)); // 注入 Trace ID
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status); // 开始构建响应
        if (exception.errorCode() == ErrorCode.RATE_LIMITED) { // 如果是限流错误
            response.header(HttpHeaders.RETRY_AFTER, "1"); // 提示 1 秒后重试
        }
        return response.body(body); // 返回最终实体
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)    // 处理 @Valid 失败
    public ResponseEntity<ApiResponse<List<FieldValidationError>>> handleMethodArgumentNotValid( // 处理函数
                                                                                                 MethodArgumentNotValidException exception, // 参数
                                                                                                 HttpServletRequest request // 参数
    ) {
        List<FieldValidationError> fieldErrors = exception.getBindingResult().getFieldErrors().stream() // 处理错误流
                .map(error -> FieldValidationError.sanitized( // 将错误清洗映射
                        error.getField(), // 获取字段名
                        resolveValidationMessage(error.getDefaultMessage()))) // 获取错误信息
                .toList(); // 转为列表
        var body = ApiResponse.failure( // 构建失败响应
                ErrorCode.VALIDATION_ERROR, // 业务码，校验错误
                ErrorCode.VALIDATION_ERROR.defaultMessage(), // 默认提示消息
                fieldErrors, // 放入详细字段错误
                TraceIdResolver.resolve(request)); // 注入追踪 ID
        return ResponseEntity.badRequest().body(body); // 返回 400 状态码响应
    }

    @ExceptionHandler(HandlerMethodValidationException.class)   // 处理方法参数校验失败
    public ResponseEntity<ApiResponse<List<FieldValidationError>>> handleHandleMethodValidation( // 函数定义
                                                                                                 HandlerMethodValidationException exception, // 参数
                                                                                                 HttpServletRequest request // 参数
    ) {
        List<FieldValidationError> fieldErrors =
                exception.getParameterValidationResults().stream() // 处理参数结果流
                        .flatMap( result -> result.getResolvableErrors().stream() // 打开错误信息
                                .map(error -> FieldValidationError.sanitized( // 映射清洗
                                        resolveParameterName(result.getMethodParameter()), // 解析参数名
                                        resolveValidationMessage(error.getDefaultMessage())))) // 解析消息
                        .toList(); // 集合化
        return validationFailure(fieldErrors, request); // 调用统一校验失败构建器
    }

    @ExceptionHandler(ConstraintViolationException.class)   // 处理单参数 Constraint 校验异常
    public ResponseEntity<ApiResponse<List<FieldValidationError>>> handleConstraintViolation(   // 定义
                                                                                                ConstraintViolationException exception, // 参数
                                                                                                HttpServletRequest request // 参数
    ) {
        List<FieldValidationError> fieldErrors = exception.getConstraintViolations().stream() // 获取违规项流
                .map(violation -> FieldValidationError.sanitized( // 映射
                        violation.getPropertyPath().toString(), // 获取路径即字段名
                        resolveValidationMessage(violation.getMessage()))) // 获取消息
                .toList(); // 列表化
        return validationFailure(fieldErrors, request); // 返回响应
    }

    @ExceptionHandler(HttpMessageNotReadableException.class) // 处理 JSON 解析失败（如格式错误）
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleUnreadableMessage( // 定义
                                                                                     HttpMessageNotReadableException exception, // 参数
                                                                                     HttpServletRequest request // 参数
    ) {
        return validationFailure( // 返回格式化错误
                List.of(FieldValidationError.sanitized("body", "请求体格式不合法")), request); // 固定提示
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class) // 处理请求方法不支持（如本该 POST 用了 GET）
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleMethodNotSupported( // 定义
                                                                                      HttpRequestMethodNotSupportedException exception, // 参数
                                                                                      HttpServletRequest request    // 参数
    )
    {
        return protocolFailure( // 返回协议错误
                exception.getStatusCode(), exception.getHeaders(), "method", "请求方法不受支持", request); // 构建信息
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class) // 处理 Content-Type 不对
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleMediaTypeNotSupported( // 定义
                                                                                         HttpMediaTypeNotSupportedException exception, // 参数
                                                                                         HttpServletRequest request    // 参数
    ){
        return protocolFailure( // 返回协议错误
                exception.getStatusCode(), exception.getHeaders(), "contentType", "媒体类型不受支持", request); // 构建信息
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class) // 处理 Accept 头不对
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleMediaTypeNotAcceptable( // 定义
                                                                                          HttpMediaTypeNotAcceptableException exception, // 参数
                                                                                          HttpServletRequest request    // 参数
    ){
        return protocolFailure( // 返回协议错误
                exception.getStatusCode(), exception.getHeaders(), "accept", "无法生成可接受的响应类型", request); // 返回
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class}) // 处理 404
    ResponseEntity<ApiResponse<Void>> handleResourceNotFound( // 定义
                                                              Exception exception, // 参数
                                                              HttpServletRequest request    // 参数
    ) {
        var errorResponse = (ErrorResponse) exception; // 强转为 ErrorResponse
        var body = ApiResponse.<Void>failure( // 构建 404 信封
                ErrorCode.RESOURCE_NOT_FOUND, // 业务码：未找到
                ErrorCode.RESOURCE_NOT_FOUND.defaultMessage(), // 提示语
                TraceIdResolver.resolve(request)); // ID
        return ResponseEntity.status(errorResponse.getStatusCode()) // 设置 HTTP 状态码
                .headers(errorResponse.getHeaders()).body(body); // 保持原有的头并返回
    }

    @ExceptionHandler({ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class}) // 处理参数确实或类型不对
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleRequestFailure( // 定义
                                                                                  Exception exception, // 参数
                                                                                  HttpServletRequest request // 参数
    ) {
        return validationFailure( // 返回校验错误
                List.of(FieldValidationError.sanitized("request", "请求参数不合法")), request); // 提示
    }

    @ExceptionHandler(Exception.class)  // 全局兜底：处理一切未知系统异常
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException( // 定义
                                                                        Exception exception, // 异常对象
                                                                        HttpServletRequest request) { // 请求对象
        var traceId = TraceIdResolver.resolve(request); // 提取 ID
        // 核心安全策略：严禁将原始的异常信息（如SQL/栈信息）返回前端
        // 仅在服务器内部日志打印，通过 TraceId 与外部报错关联。
        LOGGER.error("Unexpected exception, traceId=" + traceId + ", type=" + exception.getClass().getName()); // 打印类名
        var body = ApiResponse.<Void>failure( // 返回前端 500 错误
                ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), traceId); // 注入提示
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body); // 响应
    }

    private HttpStatus statusFor(ErrorCode errorCode) { // 临时工具
        return switch (errorCode) { // 根据枚举判断
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST; // 400
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED; // 401
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN; // 403
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;    // 404
            case BUSINESS_CONFLICT -> HttpStatus.CONFLICT; // 409
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS; // 429
            case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;  // 503
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR; // 500
            case SUCCESS -> HttpStatus.OK; // 200
        };
    }

    private String externalMessage(BusinessException exception) { // 展示消息过滤
        return switch (exception.errorCode()) { // 根据错误类型判断
            case UNAUTHENTICATED, PERMISSION_DENIED, RATE_LIMITED,
                 DEPENDENCY_UNAVAILABLE, INTERNAL_ERROR -> exception.errorCode().defaultMessage(); // 安全码强制显示默认信息
            default -> exception.getMessage();  // 业务逻辑码允许展示具体报错
        };
    }

    private String resolveValidationMessage(String message) { // 校验消息判空
        return message == null || message.isBlank() ? "字段值不合法" : message; // 默认值处理
    }

    private String resolveParameterName(org.springframework.core.MethodParameter parameter) { // 参数名解析
        String parameterName = parameter.getParameterName(); // 获取参数名
        return parameterName == null || parameterName.isEmpty() // 若无
                ? "arg" + parameter.getParameterIndex() // 使用索引代替
                : parameterName; // 使用原名
    }

    private ResponseEntity<ApiResponse<List<FieldValidationError>>> validationFailure( // 统一验证失败构建
                                                                                       List<FieldValidationError> fieldErrors, // 错误项
                                                                                       HttpServletRequest request // 请求
    ) {
        var body = ApiResponse.failure( // 构造对象
                ErrorCode.VALIDATION_ERROR, // 码
                ErrorCode.VALIDATION_ERROR.defaultMessage(), // 消息
                fieldErrors, // 数据
                TraceIdResolver.resolve(request)); // ID
        return ResponseEntity.badRequest().body(body); // 返回
    }

    private ResponseEntity<ApiResponse<List<FieldValidationError>>> protocolFailure( // 统一协议失败构建
                                                                                     org.springframework.http.HttpStatusCode status, // 状态
                                                                                     HttpHeaders headers, // 头
                                                                                     String field, // 伪字段名
                                                                                     String message, // 消息
                                                                                     HttpServletRequest request // 请求
    ) {
        var body = ApiResponse.failure( // 伪造对象
                ErrorCode.VALIDATION_ERROR, // 码
                ErrorCode.VALIDATION_ERROR.defaultMessage(), // 消息
                List.of(FieldValidationError.sanitized(field, message)), // 单个错误
                TraceIdResolver.resolve(request)); // ID
        return ResponseEntity.status(status).headers(headers).body(body); // 返回指定状态
    }
}
