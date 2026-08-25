package br.com.gestpro.plano.service;

import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.auth.repository.UsuarioRepository;
import br.com.gestpro.plano.StatusAcesso;
import br.com.gestpro.plano.TipoPlano;
import br.com.gestpro.plano.stripe.model.Assinatura;
import br.com.gestpro.plano.stripe.repository.AssinaturaRepository;
import br.com.gestpro.plano.stripe.service.StripePriceProperties;
import com.stripe.exception.StripeException;
import com.stripe.model.Price;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AtualizarPlanoOperation")
class AtualizarPlanoOperationTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AssinaturaRepository assinaturaRepository;

    @Mock
    private StripePriceProperties stripePrices;

    @InjectMocks
    private AtualizarPlanoOperation sut;

    // ── Fixtures ────────────────────────────────────────────────────────────

    private static final String EMAIL          = "cliente@example.com";
    private static final String SUBSCRIPTION_ID = "sub_abc123";
    private static final String CUSTOMER_ID     = "cus_xyz789";
    // Ajuste o priceId para um valor reconhecido pelo seu PlanoTipo.fromPriceId()
    private static final String PRICE_ID_PRO    = "price_pro_monthly";

    private Usuario   usuario;
    private Assinatura assinatura;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(7L);
        usuario.setEmail(EMAIL);

        assinatura = new Assinatura();
        assinatura.setUsuario(usuario);

        lenient().when(stripePrices.fromPriceId(PRICE_ID_PRO))
                .thenReturn(br.com.gestpro.plano.stripe.PlanoTipo.PRO);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Monta um Subscription fake com o priceId e currentPeriodEnd fornecidos.
     */
    private Subscription buildSubscription(String priceId, long currentPeriodEnd) {
        Price price = new Price();
        price.setId(priceId);

        SubscriptionItem item = new SubscriptionItem();
        item.setPrice(price);

        SubscriptionItemCollection items = new SubscriptionItemCollection();
        items.setData(List.of(item));

        Subscription subscription = new Subscription();
        subscription.setId(SUBSCRIPTION_ID);
        subscription.setStatus("active");
        subscription.setCustomer(CUSTOMER_ID);
        subscription.setMetadata(Map.of("usuarioId", String.valueOf(usuario.getId())));
        subscription.setCurrentPeriodEnd(currentPeriodEnd);
        subscription.setItems(items);

        return subscription;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ativarPlano
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ativarPlano")
    class AtivarPlano {

        @Test
        @DisplayName("deve criar nova assinatura e ativar o usuario quando não existe assinatura prévia")
        void deveCriarAssinaturaEAtivarUsuario() throws Exception {

            long periodEnd = Instant.now().plusSeconds(30 * 24 * 3600).getEpochSecond();
            Subscription subscription = buildSubscription(PRICE_ID_PRO, periodEnd);

            try (MockedStatic<Subscription> stripeStatic = mockStatic(Subscription.class)) {
                stripeStatic.when(() -> Subscription.retrieve(SUBSCRIPTION_ID))
                        .thenReturn(subscription);

                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
                when(assinaturaRepository.findByUsuarioEmail(EMAIL)).thenReturn(Optional.empty());
                when(assinaturaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                sut.ativarPlano(EMAIL, SUBSCRIPTION_ID, CUSTOMER_ID);
            }

            // Assinatura salva com os dados corretos
            ArgumentCaptor<Assinatura> assinaturaCaptor = ArgumentCaptor.forClass(Assinatura.class);
            verify(assinaturaRepository).save(assinaturaCaptor.capture());
            Assinatura salva = assinaturaCaptor.getValue();

            assertThat(salva.getStripeSubscriptionId()).isEqualTo(SUBSCRIPTION_ID);
            assertThat(salva.getStripeCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(salva.getStatus()).isEqualTo("ATIVO");
            assertThat(salva.getDataVencimento()).isEqualTo(
                    Instant.ofEpochSecond(periodEnd)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toLocalDate());

            // Usuário ativo
            ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(usuarioCaptor.capture());
            assertThat(usuarioCaptor.getValue().getStatusAcesso()).isEqualTo(StatusAcesso.ATIVO);
        }

        @Test
        @DisplayName("deve atualizar assinatura existente em vez de criar nova")
        void deveAtualizarAssinaturaExistente() throws Exception {

            long periodEnd = Instant.now().plusSeconds(30 * 24 * 3600).getEpochSecond();
            Subscription subscription = buildSubscription(PRICE_ID_PRO, periodEnd);

            try (MockedStatic<Subscription> stripeStatic = mockStatic(Subscription.class)) {
                stripeStatic.when(() -> Subscription.retrieve(SUBSCRIPTION_ID))
                        .thenReturn(subscription);

                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
                when(assinaturaRepository.findByUsuarioEmail(EMAIL)).thenReturn(Optional.of(assinatura));
                when(assinaturaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                sut.ativarPlano(EMAIL, SUBSCRIPTION_ID, CUSTOMER_ID);
            }

            // Deve ter salvo apenas uma assinatura (a existente, atualizada)
            verify(assinaturaRepository, times(1)).save(assinatura);
        }

        @Test
        @DisplayName("deve lançar RuntimeException quando usuário não encontrado")
        void deveLancarExcecaoQuandoUsuarioNaoEncontrado() throws Exception {

            long periodEnd = Instant.now().plusSeconds(30 * 24 * 3600).getEpochSecond();
            Subscription subscription = buildSubscription(PRICE_ID_PRO, periodEnd);

            try (MockedStatic<Subscription> stripeStatic = mockStatic(Subscription.class)) {
                stripeStatic.when(() -> Subscription.retrieve(SUBSCRIPTION_ID))
                        .thenReturn(subscription);

                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> sut.ativarPlano(EMAIL, SUBSCRIPTION_ID, CUSTOMER_ID))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining(EMAIL);
            }

            verify(assinaturaRepository, never()).save(any());
        }

        @Test
        @DisplayName("deve lançar RuntimeException quando Stripe falha ao buscar subscription")
        void deveLancarExcecaoQuandoStripeFalha() throws Exception {

            try (MockedStatic<Subscription> stripeStatic = mockStatic(Subscription.class)) {
                stripeStatic.when(() -> Subscription.retrieve(SUBSCRIPTION_ID))
                        .thenThrow(mock(StripeException.class));

                assertThatThrownBy(() -> sut.ativarPlano(EMAIL, SUBSCRIPTION_ID, CUSTOMER_ID))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("Falha ao obter dados da Stripe");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  renovarPlano
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("renovarPlano")
    class RenovarPlano {

        @Test
        @DisplayName("deve atualizar vencimento e manter status ATIVO")
        void deveRenovarVencimentoEManterAtivo() throws Exception {

            long novoFim = Instant.now().plusSeconds(60 * 24 * 3600).getEpochSecond();
            Subscription subscription = buildSubscription(PRICE_ID_PRO, novoFim);
            LocalDate vencimentoEsperado = Instant.ofEpochSecond(novoFim)
                    .atZone(java.time.ZoneOffset.UTC).toLocalDate();

            assinatura.setUsuario(usuario);

            try (MockedStatic<Subscription> stripeStatic = mockStatic(Subscription.class)) {
                stripeStatic.when(() -> Subscription.retrieve(SUBSCRIPTION_ID))
                        .thenReturn(subscription);

                when(assinaturaRepository.findByStripeSubscriptionId(SUBSCRIPTION_ID))
                        .thenReturn(Optional.of(assinatura));
                when(assinaturaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                sut.renovarPlano(SUBSCRIPTION_ID);
            }

            assertThat(assinatura.getDataVencimento()).isEqualTo(vencimentoEsperado);
            assertThat(assinatura.getStatus()).isEqualTo("ATIVO");
            assertThat(usuario.getStatusAcesso()).isEqualTo(StatusAcesso.ATIVO);
        }

        @Test
        @DisplayName("deve reativar usuario que estava INATIVO por atraso de pagamento")
        void deveReativarUsuarioInadimplente() throws Exception {

            usuario.setStatusAcesso(StatusAcesso.INATIVO);
            assinatura.setUsuario(usuario);

            long periodEnd = Instant.now().plusSeconds(30 * 24 * 3600).getEpochSecond();
            Subscription subscription = buildSubscription(PRICE_ID_PRO, periodEnd);

            try (MockedStatic<Subscription> stripeStatic = mockStatic(Subscription.class)) {
                stripeStatic.when(() -> Subscription.retrieve(SUBSCRIPTION_ID))
                        .thenReturn(subscription);

                when(assinaturaRepository.findByStripeSubscriptionId(SUBSCRIPTION_ID))
                        .thenReturn(Optional.of(assinatura));
                when(assinaturaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                sut.renovarPlano(SUBSCRIPTION_ID);
            }

            assertThat(usuario.getStatusAcesso()).isEqualTo(StatusAcesso.ATIVO);
        }

        @Test
        @DisplayName("deve lançar RuntimeException quando assinatura não encontrada")
        void deveLancarExcecaoQuandoAssinaturaNaoEncontrada() throws Exception {

            long periodEnd = Instant.now().plusSeconds(30 * 24 * 3600).getEpochSecond();
            Subscription subscription = buildSubscription(PRICE_ID_PRO, periodEnd);

            try (MockedStatic<Subscription> stripeStatic = mockStatic(Subscription.class)) {
                stripeStatic.when(() -> Subscription.retrieve(SUBSCRIPTION_ID))
                        .thenReturn(subscription);

                when(assinaturaRepository.findByStripeSubscriptionId(SUBSCRIPTION_ID))
                        .thenReturn(Optional.empty());

                assertThatThrownBy(() -> sut.renovarPlano(SUBSCRIPTION_ID))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining(SUBSCRIPTION_ID);
            }

            verify(assinaturaRepository, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  cancelarPlano
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("cancelarPlano")
    class CancelarPlano {

        @Test
        @DisplayName("deve marcar assinatura como CANCELADO e rebaixar usuario para EXPERIMENTAL/INATIVO")
        void deveCancelarAssinaturaEBloquearUsuario() {

            usuario.setTipoPlano(TipoPlano.PRO);
            assinatura.setUsuario(usuario);
            when(assinaturaRepository.findByStripeSubscriptionId(SUBSCRIPTION_ID))
                    .thenReturn(Optional.of(assinatura));

            sut.cancelarPlano(SUBSCRIPTION_ID);

            assertThat(assinatura.getStatus()).isEqualTo("CANCELADO");
            assertThat(usuario.getTipoPlano()).isEqualTo(TipoPlano.PRO);
            assertThat(usuario.getStatusAcesso()).isEqualTo(StatusAcesso.INATIVO);

            verify(assinaturaRepository).save(assinatura);
            verify(usuarioRepository).save(usuario);
        }

        @Test
        @DisplayName("deve apenas logar warning quando subscription não encontrada, sem lançar exceção")
        void deveIgnorarQuandoSubscriptionNaoExiste() {

            when(assinaturaRepository.findByStripeSubscriptionId(SUBSCRIPTION_ID))
                    .thenReturn(Optional.empty());

            // Não deve lançar exceção
            sut.cancelarPlano(SUBSCRIPTION_ID);

            verify(assinaturaRepository, never()).save(any());
            verify(usuarioRepository, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  marcarInadimplente
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("marcarInadimplente")
    class MarcarInadimplente {

        @Test
        @DisplayName("deve marcar assinatura como INADIMPLENTE e bloquear acesso do usuario")
        void deveMarcarInadimplenteEBloquearUsuario() {

            assinatura.setUsuario(usuario);
            when(assinaturaRepository.findByStripeSubscriptionId(SUBSCRIPTION_ID))
                    .thenReturn(Optional.of(assinatura));

            sut.marcarInadimplente(SUBSCRIPTION_ID);

            assertThat(assinatura.getStatus()).isEqualTo("INADIMPLENTE");
            assertThat(usuario.getStatusAcesso()).isEqualTo(StatusAcesso.INATIVO);

            verify(assinaturaRepository).save(assinatura);
            verify(usuarioRepository).save(usuario);
        }

        @Test
        @DisplayName("deve apenas logar warning quando subscription não encontrada, sem lançar exceção")
        void deveIgnorarQuandoSubscriptionNaoExiste() {

            when(assinaturaRepository.findByStripeSubscriptionId(SUBSCRIPTION_ID))
                    .thenReturn(Optional.empty());

            sut.marcarInadimplente(SUBSCRIPTION_ID);

            verify(assinaturaRepository, never()).save(any());
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("deve preservar o TipoPlano do usuario ao marcar inadimplência (apenas bloqueia acesso)")
        void devePreservarTipoPlanoAoMarcarInadimplente() {

            usuario.setTipoPlano(TipoPlano.PRO); // plano pago atual
            assinatura.setUsuario(usuario);

            when(assinaturaRepository.findByStripeSubscriptionId(SUBSCRIPTION_ID))
                    .thenReturn(Optional.of(assinatura));

            sut.marcarInadimplente(SUBSCRIPTION_ID);

            // Inadimplência só bloqueia acesso; não deve rebaixar o plano
            assertThat(usuario.getTipoPlano()).isEqualTo(TipoPlano.PRO);
            assertThat(usuario.getStatusAcesso()).isEqualTo(StatusAcesso.INATIVO);
        }
    }
}
