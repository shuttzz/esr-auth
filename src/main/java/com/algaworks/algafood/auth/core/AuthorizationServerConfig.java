package com.algaworks.algafood.auth.core;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.CompositeTokenGranter;
import org.springframework.security.oauth2.provider.TokenGranter;
import org.springframework.security.oauth2.provider.approval.ApprovalStore;
import org.springframework.security.oauth2.provider.approval.TokenApprovalStore;
import org.springframework.security.oauth2.provider.token.TokenEnhancerChain;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.KeyStoreKeyFactory;

import java.util.Arrays;

@Configuration
@EnableAuthorizationServer
public class AuthorizationServerConfig extends AuthorizationServerConfigurerAdapter {

    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtKeyStoreProperties jwtKeyStoreProperties;

    public AuthorizationServerConfig(PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, UserDetailsService userDetailsService, JwtKeyStoreProperties jwtKeyStoreProperties) {
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtKeyStoreProperties = jwtKeyStoreProperties;
    }

    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
        /**
         * Os dois primeiros clientes são a configuração para o grant_type password_flow
         * O terceiro cliente é para o grant_type client_credentials
         * O quarto cliente é para o grant_type authorization_code
         */
        clients
                .inMemory()
                    .withClient("algafood-web")
                    .secret(passwordEncoder.encode("290587"))
                    .authorizedGrantTypes("password", "refresh_token")
                    .scopes("WRITE", "READ")
                .and()
                    .withClient("algafood-mobile")
                    .secret(passwordEncoder.encode("290587"))
                    .authorizedGrantTypes("password", "refresh_token")
                    .scopes("WRITE", "READ")
                    .accessTokenValiditySeconds(60 * 60) // Aqui estou definindo o token para ter validade de 1 hora
                    .refreshTokenValiditySeconds(60 * 60 * 2)
                .and()
                    .withClient("aplicacao-backend")
                    .secret(passwordEncoder.encode("290587"))
                    .authorizedGrantTypes("client_credentials")
                    .scopes("READ")
                .and()
                    .withClient("foodanalytics")
                    .secret(passwordEncoder.encode("290587"))
                    .authorizedGrantTypes("authorization_code", "refresh_token")
                    .scopes("WRITE", "READ")
                    .redirectUris("http://aplicacao-cliente")
                    .accessTokenValiditySeconds(60 * 60) // Aqui estou definindo o token para ter validade de 1 hora
                    .refreshTokenValiditySeconds(60 * 60 * 2); // Aqui estou definindo o refresh_token para ter a validade de duas horas
    }

    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
        security.checkTokenAccess("isAuthenticated()")
            .tokenKeyAccess("permitAll()");
    }

    /**
     * Somente o flow authenticate password necessita desse AuthenticateManager
     * @param endpoints
     * @throws Exception
     */
    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
        var enhancerChain = new TokenEnhancerChain();
        enhancerChain.setTokenEnhancers(Arrays.asList(new JwtCustomClaimsTokenEnhancer(), jwtAccessTokenConverter()));
        endpoints
                .authenticationManager(authenticationManager)
                .userDetailsService(userDetailsService)
                .reuseRefreshTokens(false) // Com esse código eu desabilito o reuso do refresh_token
                .accessTokenConverter(jwtAccessTokenConverter())
                .tokenEnhancer(enhancerChain)
                .approvalStore(approvalStore(endpoints.getTokenStore())) // Tem que ser chamado após o jwtAccessTokenConverter
                .tokenGranter(tokenGranter(endpoints));
    }

    private ApprovalStore approvalStore(TokenStore tokenStore) {
        var approvalStore = new TokenApprovalStore();
        approvalStore.setTokenStore(tokenStore);
        return approvalStore;
    }

    @Bean
    public JwtAccessTokenConverter jwtAccessTokenConverter() {
        JwtAccessTokenConverter jwtAccessTokenConverter = new JwtAccessTokenConverter();

        // Essa linha gerava o token com chave simétrica, agora vamos fazer com chave assimétrica
//        jwtAccessTokenConverter.setSigningKey("agfafasf122156fasdfa5sdf45asdf10a2sd1f5adsfa4sdf4asdf");
        var jksResource = new ClassPathResource(jwtKeyStoreProperties.getPath());
        var keystorePass = jwtKeyStoreProperties.getPassword();
        var keyPairAlias = jwtKeyStoreProperties.getKeypairAlias();

        var keyStoreKeyFactory = new KeyStoreKeyFactory(jksResource, keystorePass.toCharArray());
        var keyPair = keyStoreKeyFactory.getKeyPair(keyPairAlias);

        jwtAccessTokenConverter.setKeyPair(keyPair);

        return jwtAccessTokenConverter;
    }

    // Parte da configuração do Authorization Server, para adicionar o TokenGranter customizado
    private TokenGranter tokenGranter(AuthorizationServerEndpointsConfigurer endpoints) {
        var pkceAuthorizationCodeTokenGranter = new PkceAuthorizationCodeTokenGranter(endpoints.getTokenServices(),
                endpoints.getAuthorizationCodeServices(), endpoints.getClientDetailsService(),
                endpoints.getOAuth2RequestFactory());

        var granters = Arrays.asList(
                pkceAuthorizationCodeTokenGranter, endpoints.getTokenGranter());

        return new CompositeTokenGranter(granters);
    }

}
