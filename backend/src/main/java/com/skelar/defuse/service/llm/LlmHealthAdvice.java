package com.skelar.defuse.service.llm;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
/** Additive runtime provenance without changing the shared health controller or its existing keys. */
@RestControllerAdvice
public class LlmHealthAdvice implements ResponseBodyAdvice<Object> {
    private final LlmSettings settings;
    public LlmHealthAdvice(LlmSettings settings) { this.settings=settings; }
    public boolean supports(MethodParameter p,Class<? extends HttpMessageConverter<?>> converter) {
        return "DemoController".equals(p.getContainingClass().getSimpleName());
    }
    public Object beforeBodyWrite(Object body,MethodParameter p,MediaType media,
            Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        if (!"/api/health".equals(request.getURI().getPath()) || !(body instanceof Map<?,?> map)) return body;
        Map<Object,Object> out=new LinkedHashMap<>(map); out.put("provider",settings.provider()); out.put("model",settings.model());
        response.getHeaders().set("X-Defuse-LLM-Mode",settings.mode());
        return out;
    }
}
