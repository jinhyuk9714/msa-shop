package com.msa.shop.product.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 루트(/) 접속 시 Swagger UI로 리다이렉트. Whitelabel 404 방지.
 */
@Controller
public class RootRedirectController {

    @GetMapping("/")
    public String redirectToApiDocs() {
        return "redirect:/api-docs.html";
    }
}
