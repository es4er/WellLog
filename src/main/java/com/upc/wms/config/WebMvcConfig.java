package com.upc.wms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc配置类：解决跨域问题
 * @Configuration：标识为Spring配置类
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {





    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:C:/upload/file/")
                .setCachePeriod(3600)
                .resourceChain(true);


        registry.addResourceHandler("/temp/**")
                .addResourceLocations("file:C:/upload/temp/")
                .setCachePeriod(3600)
                .resourceChain(true);
    }
}