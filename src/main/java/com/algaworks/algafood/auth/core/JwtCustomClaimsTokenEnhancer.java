package com.algaworks.algafood.auth.core;

import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;

import java.util.HashMap;

public class JwtCustomClaimsTokenEnhancer implements TokenEnhancer {

    @Override
    public OAuth2AccessToken enhance(OAuth2AccessToken oAuth2AccessToken, OAuth2Authentication oAuth2Authentication) {
        if (oAuth2Authentication.getPrincipal() instanceof AuthUser) {
            var authUser = (AuthUser) oAuth2Authentication.getPrincipal();
            var info = new HashMap<String, Object>();
            info.put("nome_completo", authUser.getFullName());
            info.put("usuario_id", authUser.getUserId());
            var accesToken = (DefaultOAuth2AccessToken) oAuth2AccessToken;
            accesToken.setAdditionalInformation(info);
        }

        return oAuth2AccessToken;
    }

}
