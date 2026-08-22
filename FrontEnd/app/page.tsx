"use client";

import { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { ArrowRight, BarChart3, Boxes, Building2, Check, ChevronDown, Download, Globe2, Menu, ReceiptText, ShoppingCart, Smartphone, Wallet, X } from "lucide-react";
import { LanguageSelector } from "@/components/language-selector";
import { WhatsAppFloat } from "@/components/whatsapp-float";
import { useLanguage } from "@/components/language-provider";

const landingCopy = {
  pt: { about: "Sobre", resources: "Recursos", plans: "Planos", login: "Entrar", start: "Começar agora", eyebrow: "Gestão para pequenos negócios", title: "Controle sua loja sem depender de", accent: "planilhas e processos manuais", intro: "O Gevyro reúne vendas, caixa, estoque e relatórios para você acompanhar a operação em um único lugar.", note: "Feito para mercados, lojas e comércios que precisam trabalhar com mais organização.", trial: "Testar gratuitamente", routine: "Um sistema para sua rotina", why: "Por que usar o", problem: "Controles separados dificultam a conferência do caixa, escondem perdas de estoque e atrasam decisões.", solution: "O Gevyro organiza a operação para que cada venda gere informação útil para o negócio.", conclusion: "Menos tempo conferindo controles. Mais clareza para cuidar da loja.", results: "Resultado na rotina", helps: "Como o Gevyro ajuda", business: "seu negócio", essential: "O essencial para a operação", nonstop: "não parar", choose: "Escolha o plano da", operation: "sua operação", recommended: "Recomendado", choosePlan: "Escolher plano", questions: "Perguntas", frequent: "frequentes", organize: "Comece a organizar", store: "sua loja", test: "Teste o Gevyro e conheça a rotina do sistema.", create: "Criar minha conta", service: "Atendimento", how: "Como usar", contact: "Fale conosco", client: "Área do cliente", transparency: "Transparência", terms: "Termos de uso", privacy: "Política de privacidade", support: "Suporte", rights: "Todos os direitos reservados.", made: "Plataforma de gestão desenvolvida no Brasil.", points: ["Vendas e pagamentos registrados", "Estoque atualizado automaticamente", "Informações centralizadas", "Resultados por período", "Controle de várias empresas"] },
  en: { about: "About", resources: "Features", plans: "Plans", login: "Sign in", start: "Get started", eyebrow: "Management for small businesses", title: "Run your store without relying on", accent: "spreadsheets and manual processes", intro: "Gevyro brings sales, cash register, inventory and reports together so you can manage your operation in one place.", note: "Built for markets, stores and retailers that need a more organized routine.", trial: "Try it free", routine: "A system built for your routine", why: "Why choose", problem: "Separate controls make cash reconciliation harder, hide inventory losses and delay decisions.", solution: "Gevyro organizes the operation so every sale produces useful business information.", conclusion: "Less time checking controls. More clarity to run your store.", results: "Everyday results", helps: "How Gevyro helps", business: "your business", essential: "Everything your operation needs to", nonstop: "keep moving", choose: "Choose the right plan for", operation: "your operation", recommended: "Recommended", choosePlan: "Choose plan", questions: "Frequently asked", frequent: "questions", organize: "Start organizing", store: "your store", test: "Try Gevyro and discover a simpler operating routine.", create: "Create my account", service: "Support", how: "How to use", contact: "Contact us", client: "Customer area", transparency: "Transparency", terms: "Terms of use", privacy: "Privacy policy", support: "Support", rights: "All rights reserved.", made: "Business management platform developed in Brazil.", points: ["Sales and payments recorded", "Inventory updated automatically", "Information in one place", "Results by period", "Multi-company control"] },
  es: { about: "Nosotros", resources: "Recursos", plans: "Planes", login: "Ingresar", start: "Comenzar ahora", eyebrow: "Gestión para pequeños negocios", title: "Controla tu tienda sin depender de", accent: "hojas de cálculo y procesos manuales", intro: "Gevyro reúne ventas, caja, inventario e informes para administrar tu operación en un solo lugar.", note: "Creado para mercados, tiendas y comercios que necesitan trabajar con más organización.", trial: "Probar gratis", routine: "Un sistema para tu rutina", why: "¿Por qué usar", problem: "Los controles separados dificultan la conciliación de caja, esconden pérdidas de inventario y retrasan decisiones.", solution: "Gevyro organiza la operación para que cada venta genere información útil para el negocio.", conclusion: "Menos tiempo revisando controles. Más claridad para cuidar tu tienda.", results: "Resultados diarios", helps: "Cómo Gevyro ayuda a", business: "tu negocio", essential: "Lo esencial para que la operación", nonstop: "no se detenga", choose: "Elige el plan para", operation: "tu operación", recommended: "Recomendado", choosePlan: "Elegir plan", questions: "Preguntas", frequent: "frecuentes", organize: "Comienza a organizar", store: "tu tienda", test: "Prueba Gevyro y descubre la rutina del sistema.", create: "Crear mi cuenta", service: "Atención", how: "Cómo usar", contact: "Contáctanos", client: "Área del cliente", transparency: "Transparencia", terms: "Términos de uso", privacy: "Política de privacidad", support: "Soporte", rights: "Todos los derechos reservados.", made: "Plataforma de gestión desarrollada en Brasil.", points: ["Ventas y pagos registrados", "Inventario actualizado automáticamente", "Información centralizada", "Resultados por período", "Control de varias empresas"] },
};

const structuredData = {
  "@context": "https://schema.org",
  "@graph": [
    {
      "@type": "Organization",
      "@id": "https://www.gevyro.com.br/#organization",
      name: "Gevyro",
      url: "https://www.gevyro.com.br/",
      logo: "https://www.gevyro.com.br/images/gevyro-logo-400.webp",
      description: "Software de gestão empresarial para vendas, estoque, clientes, caixa e resultados.",
      slogan: "Gestão em evolução.",
    },
    {
      "@type": "WebSite",
      "@id": "https://www.gevyro.com.br/#website",
      url: "https://www.gevyro.com.br/",
      name: "Gevyro",
      description: "Software de gestão empresarial para organizar a operação de pequenos negócios.",
      inLanguage: "pt-BR",
      publisher: { "@id": "https://www.gevyro.com.br/#organization" },
    },
    {
      "@type": "SoftwareApplication",
      "@id": "https://www.gevyro.com.br/#software",
      name: "Gevyro",
      url: "https://www.gevyro.com.br/",
      applicationCategory: "BusinessApplication",
      operatingSystem: "Web",
      description: "Plataforma de gestão empresarial para vendas, produtos, estoque, clientes, caixa e relatórios.",
      provider: { "@id": "https://www.gevyro.com.br/#organization" },
      inLanguage: "pt-BR",
    },
  ],
};

const benefits = [
  { icon: ShoppingCart, title: "Vendas mais rápidas", text: "Registre produtos, pagamentos, descontos e troco no mesmo fluxo." },
  { icon: Boxes, title: "Estoque atualizado", text: "Cada venda dá baixa nos produtos e mantém as quantidades organizadas." },
  { icon: Wallet, title: "Caixa sob controle", text: "Acompanhe abertura, movimentações e fechamento sem controles paralelos." },
  { icon: BarChart3, title: "Números mais claros", text: "Consulte receita, lucro, ticket médio e formas de pagamento." },
  { icon: Building2, title: "Uma ou várias lojas", text: "Separe empresas, caixas e estoques usando uma única conta." },
  { icon: ReceiptText, title: "Tudo em um só lugar", text: "Centralize vendas, produtos, clientes e fornecedores sem controles separados." },
];

const areas = [
  { label: "Vendas", icon: ReceiptText, title: "Venda e receba sem interromper o atendimento", text: "O caixa reúne produtos, descontos, pagamentos e emissão de cupom em uma tela direta.", image: "/images/landing/gevyro-feature-vendas.avif", imageAlt: "Visão geral de vendas e indicadores no painel da Gevyro" },
  { label: "Estoque", icon: Boxes, title: "Saiba o que entrou, saiu e precisa ser reposto", text: "Cadastre produtos, acompanhe quantidades e receba alertas antes que um item acabe.", image: "/images/landing/gevyro-feature-estoque.avif", imageAlt: "Tela de movimentações e vendas da operação no sistema Gevyro" },
  { label: "Relatórios", icon: BarChart3, title: "Entenda o resultado sem montar planilhas", text: "Visualize vendas, lucro, ticket médio, produtos e pagamentos por período.", image: "/images/landing/gevyro-feature-relatorios.avif", imageAlt: "Relatórios de vendas, pagamentos e desempenho no sistema Gevyro" },
];

const plans = [
  { name: "Experimental", period: "30 dias", limits: "1 empresa e 1 caixa", features: ["Frente de caixa", "Estoque", "Resumo do negócio"] },
  { name: "Básico", period: "Mensal", limits: "1 empresa e 1 caixa", features: ["Recursos essenciais", "Relatórios", "Clientes e fornecedores"] },
  { name: "Pro", period: "Mensal", limits: "Até 5 empresas e 5 caixas", features: ["Operação multiempresa", "Mais caixas", "Exportação de relatórios"], featured: true },
  { name: "Premium", period: "Mensal", limits: "Empresas e caixas ilimitados", features: ["Todos os recursos", "Unidades sem limite", "Integrações com Shopee e Mercado Livre"] },
];

const faqs = [
  { q: "Preciso entender de sistemas para usar o Gevyro?", a: "Não. As telas foram organizadas para a rotina de pequenos negócios e podem ser usadas desde o primeiro acesso." },
  { q: "Posso gerenciar mais de uma empresa?", a: "Sim. Os planos compatíveis permitem separar empresas, caixas, estoques e resultados na mesma conta." },
  { q: "O estoque muda quando uma venda é registrada?", a: "Sim. Os itens vendidos são descontados automaticamente e voltam ao estoque quando uma venda é cancelada." },
  { q: "Consigo acessar pelo celular?", a: "Sim. O Gevyro funciona pela internet e pode ser acessado pelo computador, tablet ou celular." },
];

const translatedData = {
  en: {
    benefits: [
      { icon: ShoppingCart, title: "Faster sales", text: "Record products, payments, discounts and change in one flow." }, { icon: Boxes, title: "Updated inventory", text: "Every sale deducts items and keeps quantities organized." }, { icon: Wallet, title: "Cash under control", text: "Track opening, movements and closing without parallel controls." }, { icon: BarChart3, title: "Clearer numbers", text: "Review revenue, profit, average ticket and payment methods." }, { icon: Building2, title: "One or multiple stores", text: "Separate companies, registers and inventory under one account." }, { icon: ReceiptText, title: "Everything in one place", text: "Centralize sales, products, customers and suppliers." },
    ],
    areas: [
      { ...areas[0], label: "Sales", title: "Sell and collect without interrupting service", text: "The register combines products, discounts, payments and receipts in one direct screen." }, { ...areas[1], label: "Inventory", title: "Know what came in, went out and needs restocking", text: "Register products, track quantities and receive alerts before an item runs out." }, { ...areas[2], label: "Reports", title: "Understand results without building spreadsheets", text: "Review sales, profit, average ticket, products and payments by period." },
    ],
    plans: [
      { ...plans[0], period: "30 days", limits: "1 company and 1 register", features: ["Point of sale", "Inventory", "Business overview"] }, { ...plans[1], name: "Basic", period: "Monthly", limits: "1 company and 1 register", features: ["Essential features", "Reports", "Customers and suppliers"] }, { ...plans[2], period: "Monthly", limits: "Up to 5 companies and 5 registers", features: ["Multi-company operation", "More registers", "Report exports"] }, { ...plans[3], period: "Monthly", limits: "Unlimited companies and registers", features: ["All features", "Unlimited locations", "Shopee and Mercado Livre integrations"] },
    ],
    faqs: [
      { q: "Do I need technical knowledge to use Gevyro?", a: "No. The screens follow the routine of small businesses and can be used from the first access." }, { q: "Can I manage more than one company?", a: "Yes. Compatible plans keep companies, registers, inventory and results separate in one account." }, { q: "Does inventory change after a sale?", a: "Yes. Sold items are deducted automatically and returned when a sale is cancelled." }, { q: "Can I access it on mobile?", a: "Yes. Gevyro works online on computers, tablets and mobile phones." },
    ],
  },
  es: {
    benefits: [
      { icon: ShoppingCart, title: "Ventas más rápidas", text: "Registra productos, pagos, descuentos y cambio en un solo flujo." }, { icon: Boxes, title: "Inventario actualizado", text: "Cada venta descuenta artículos y mantiene las cantidades organizadas." }, { icon: Wallet, title: "Caja bajo control", text: "Controla apertura, movimientos y cierre sin controles paralelos." }, { icon: BarChart3, title: "Números más claros", text: "Consulta ingresos, ganancia, ticket medio y formas de pago." }, { icon: Building2, title: "Una o varias tiendas", text: "Separa empresas, cajas e inventarios con una sola cuenta." }, { icon: ReceiptText, title: "Todo en un solo lugar", text: "Centraliza ventas, productos, clientes y proveedores." },
    ],
    areas: [
      { ...areas[0], label: "Ventas", title: "Vende y cobra sin interrumpir la atención", text: "La caja reúne productos, descuentos, pagos y recibos en una pantalla directa." }, { ...areas[1], label: "Inventario", title: "Conoce qué entró, salió y necesita reposición", text: "Registra productos, controla cantidades y recibe alertas antes de que se agoten." }, { ...areas[2], label: "Informes", title: "Entiende los resultados sin crear hojas de cálculo", text: "Consulta ventas, ganancia, ticket medio, productos y pagos por período." },
    ],
    plans: [
      { ...plans[0], period: "30 días", limits: "1 empresa y 1 caja", features: ["Punto de venta", "Inventario", "Resumen del negocio"] }, { ...plans[1], name: "Básico", period: "Mensual", limits: "1 empresa y 1 caja", features: ["Recursos esenciales", "Informes", "Clientes y proveedores"] }, { ...plans[2], period: "Mensual", limits: "Hasta 5 empresas y 5 cajas", features: ["Operación multiempresa", "Más cajas", "Exportación de informes"] }, { ...plans[3], period: "Mensual", limits: "Empresas y cajas ilimitadas", features: ["Todos los recursos", "Sucursales ilimitadas", "Integraciones con Shopee y Mercado Livre"] },
    ],
    faqs: [
      { q: "¿Necesito conocimientos técnicos para usar Gevyro?", a: "No. Las pantallas siguen la rutina de pequeños negocios y pueden usarse desde el primer acceso." }, { q: "¿Puedo administrar más de una empresa?", a: "Sí. Los planes compatibles separan empresas, cajas, inventarios y resultados en una cuenta." }, { q: "¿El inventario cambia al registrar una venta?", a: "Sí. Los artículos se descuentan automáticamente y regresan cuando se cancela la venta." }, { q: "¿Puedo acceder desde el móvil?", a: "Sí. Gevyro funciona por internet en computadoras, tabletas y móviles." },
    ],
  },
};

function Header() {
  const [open, setOpen] = useState(false);
  const { language } = useLanguage();
  const c = landingCopy[language];
  return (
    <header className="sticky top-0 z-50 border-b border-[#e7ece9] bg-white/95 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-[1480px] items-center justify-between px-4 sm:h-[72px] sm:px-8 lg:h-[78px] xl:px-12">
        <Link href="/" className="flex items-center gap-3">
          <Image src="/images/gevyro-logo-400.webp" alt="Gevyro" width={400} height={145} priority className="h-auto w-[148px] object-contain sm:w-[180px] lg:w-[200px]" />
        </Link>
        <nav className="hidden items-center gap-8 text-[14px] font-medium text-[#18221d] lg:flex">
          <a href="#sobre" className="hover:text-[#238a52]">{c.about}</a><a href="#recursos" className="hover:text-[#238a52]">{c.resources}</a><a href="#planos" className="hover:text-[#238a52]">{c.plans}</a><a href="#faq" className="hover:text-[#238a52]">FAQ</a><Link href="/auth/login" className="hover:text-[#238a52]">{c.login}</Link>
          <LanguageSelector />
          <Link href="/auth/cadastro" className="rounded-full bg-[#087f47] px-7 py-3.5 text-[11px] font-bold uppercase tracking-[.08em] text-white shadow-[0_10px_25px_rgba(8,127,71,.18)] transition hover:-translate-y-0.5 hover:bg-[#066b3c]">{c.start}</Link>
        </nav>
        <button type="button" onClick={() => setOpen(!open)} className="grid h-10 w-10 place-items-center rounded-full border border-[#e3e9e6] text-[#27302b] lg:hidden" aria-expanded={open} aria-label={open ? "Fechar menu" : "Abrir menu"}>{open ? <X size={20} /> : <Menu size={20} />}</button>
      </div>
      {open && <nav className="absolute inset-x-0 top-full border-t border-zinc-100 bg-white/98 px-4 py-4 shadow-[0_20px_45px_rgba(24,34,29,.12)] lg:hidden"><div className="mx-auto grid max-w-6xl grid-cols-2 gap-2 text-sm font-medium text-[#27302b]"><a href="#sobre" onClick={() => setOpen(false)} className="rounded-xl bg-[#f4f7f5] px-4 py-3">{c.about}</a><a href="#recursos" onClick={() => setOpen(false)} className="rounded-xl bg-[#f4f7f5] px-4 py-3">{c.resources}</a><a href="#planos" onClick={() => setOpen(false)} className="rounded-xl bg-[#f4f7f5] px-4 py-3">{c.plans}</a><a href="#faq" onClick={() => setOpen(false)} className="rounded-xl bg-[#f4f7f5] px-4 py-3">FAQ</a><div className="col-span-2 flex items-center justify-between border-y border-zinc-100 py-3"><LanguageSelector /><Link href="/auth/login" onClick={() => setOpen(false)} className="px-3 text-[#087f47]">{c.login}</Link></div><Link href="/auth/cadastro" onClick={() => setOpen(false)} className="col-span-2 rounded-full bg-[#258c53] px-6 py-3.5 text-center font-bold text-white">{c.start}</Link></div></nav>}
    </header>
  );
}

function Hero() {
  const { language } = useLanguage();
  const c = landingCopy[language];
  const hero = {
    pt: { title: "Gestão inteligente para empresas que querem", accent: "crescer com controle.", intro: "Vendas, estoque, caixa e resultados conectados em uma única plataforma.", trial: "Teste grátis por 30 dias", card: "Sem cartão de crédito", features: [["Vendas", "mais rápidas"], ["Estoque", "sempre atualizado"], ["Caixa", "organizado"], ["Relatórios", "que geram lucro"]] },
    en: { title: "Smart management for companies ready to", accent: "grow with control.", intro: "Sales, inventory, cash flow and results connected in one platform.", trial: "30-day free trial", card: "No credit card", features: [["Sales", "faster"], ["Inventory", "always updated"], ["Cash flow", "organized"], ["Reports", "that drive profit"]] },
    es: { title: "Gestión inteligente para empresas que quieren", accent: "crecer con control.", intro: "Ventas, inventario, caja y resultados conectados en una única plataforma.", trial: "Prueba gratis por 30 días", card: "Sin tarjeta de crédito", features: [["Ventas", "más rápidas"], ["Inventario", "siempre actualizado"], ["Caja", "organizada"], ["Informes", "que generan ganancias"]] },
  }[language];
  const featureIcons = [ShoppingCart, Boxes, Wallet, BarChart3];
  return (
    <section className="relative overflow-hidden bg-white">
      <div className="pointer-events-none absolute right-[4%] top-[10%] h-[520px] w-[520px] rounded-full bg-[radial-gradient(circle,rgba(16,185,129,.09),rgba(255,255,255,0)_68%)]" />
      <div className="mx-auto grid max-w-[1480px] items-center gap-6 px-4 py-9 sm:gap-10 sm:px-8 sm:py-14 lg:min-h-[calc(100svh-78px)] lg:grid-cols-[.78fr_1.22fr] lg:gap-4 lg:py-16 xl:px-12">
        <div className="relative z-10 max-w-[610px]">
          <p className="mb-4 text-[10px] font-bold uppercase tracking-[.16em] text-[#087f47] sm:mb-6 sm:text-[12px]">{c.eyebrow}</p>
          <h1 className="text-[36px] font-semibold leading-[1.06] tracking-[-.045em] text-[#111a16] min-[390px]:text-[39px] sm:text-[60px] xl:text-[68px]">{hero.title} <span className="text-[#087f47]">{hero.accent}</span></h1>
          <p className="mt-5 max-w-xl text-[15px] leading-6 text-[#4f5f57] sm:mt-7 sm:text-[19px] sm:leading-8">{hero.intro}</p>

          <div className="mt-6 grid max-w-[500px] grid-cols-2 gap-2.5 sm:mt-8 sm:grid-cols-4 sm:gap-x-5 sm:gap-y-5">
            {hero.features.map(([title, text], index) => {
              const Icon = featureIcons[index];
              return <div key={title} className="flex items-center gap-2 rounded-xl border border-[#e6ece9] bg-white/85 p-2 sm:block sm:border-0 sm:bg-transparent sm:p-0"><span className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-[#edf7f1] text-[#087f47] sm:h-11 sm:w-11 sm:rounded-xl"><Icon size={18} strokeWidth={1.8} /></span><div className="min-w-0 sm:mt-2"><strong className="block truncate text-[11px] text-[#18221d] sm:text-[12px]">{title}</strong><span className="block truncate text-[9px] leading-4 text-[#68776f] sm:text-[10px]">{text}</span></div></div>;
            })}
          </div>

          <div className="mt-6 flex flex-col gap-3 sm:mt-9 sm:flex-row sm:items-center sm:gap-4">
            <Link href="/auth/cadastro" className="inline-flex h-[52px] w-full items-center justify-center gap-4 rounded-full bg-[#087f47] px-6 text-[11px] font-bold uppercase tracking-[.08em] text-white shadow-[0_15px_35px_rgba(8,127,71,.2)] transition hover:-translate-y-0.5 hover:bg-[#066b3c] sm:h-[58px] sm:w-auto sm:px-8 sm:text-[12px]">{c.trial} <ArrowRight size={17} /></Link>
            <div className="space-y-1 text-[12px] text-[#3e4d45]"><span className="flex items-center gap-2"><Check size={15} className="text-[#087f47]" />{hero.trial}</span><span className="flex items-center gap-2"><Check size={15} className="text-[#087f47]" />{hero.card}</span></div>
          </div>
        </div>

        <div className="hero-laptop-stage relative mx-auto -mb-2 w-[112%] max-w-[890px] -translate-x-[5%] sm:mb-0 sm:w-full sm:translate-x-0 lg:-mr-12 xl:-mr-20">
          <div className="hero-laptop-shadow" aria-hidden="true" />
          <div className="hero-laptop-photo relative aspect-[3/2] w-full">
            <Image src="/images/landing/gevyro-dashboard-laptop-white-v1.webp" alt="Notebook real exibindo o dashboard de gestão da Gevyro" fill priority className="object-contain mix-blend-multiply" sizes="(max-width: 1024px) 100vw, 62vw" />
          </div>
          <div className="hero-laptop-card absolute left-[5%] top-[17%] hidden w-[150px] rounded-2xl border border-[#e4ebe7] bg-white/95 p-4 shadow-[0_18px_45px_rgba(26,57,42,.12)] backdrop-blur xl:block"><span className="grid h-9 w-9 place-items-center rounded-full bg-[#e9f7ef] text-[#087f47]"><ShoppingCart size={17} /></span><p className="mt-3 text-[11px] text-[#5d6b64]">Vendas do mês</p><strong className="mt-1 block text-[16px] text-[#17231d]">R$ 12.694,80</strong><span className="mt-2 block text-[10px] font-semibold text-[#0b9c58]">+18,6% no período</span></div>
          <div className="hero-laptop-card hero-laptop-card-delay absolute bottom-[12%] right-[1%] hidden w-[145px] rounded-2xl border border-[#e4ebe7] bg-white/95 p-4 shadow-[0_18px_45px_rgba(26,57,42,.12)] backdrop-blur xl:block"><span className="grid h-9 w-9 place-items-center rounded-full bg-[#e9f7ef] text-[#087f47]"><Wallet size={17} /></span><p className="mt-3 text-[11px] text-[#5d6b64]">Caixa aberto</p><strong className="mt-1 block text-[16px] text-[#17231d]">R$ 1.250,00</strong><div className="mt-3 h-1.5 overflow-hidden rounded-full bg-[#e7eeea]"><span className="block h-full w-3/4 rounded-full bg-[#0a9856]" /></div></div>
        </div>
      </div>
    </section>
  );
}

function About() {
  const { language } = useLanguage(); const c = landingCopy[language]; const points = c.points;
  return (
    <section id="sobre" className="bg-white py-14 sm:py-20 lg:py-28"><div className="mx-auto max-w-6xl px-4 sm:px-8 lg:px-0">
      <p className="text-[10px] font-bold uppercase tracking-[.14em] text-[#258c53] sm:text-[11px]">{c.routine}</p><h2 className="mt-3 max-w-xl text-[32px] font-light leading-tight text-[#343b37] sm:mt-4 sm:text-4xl lg:text-5xl">{c.why} <span className="italic text-[#258c53]">Gevyro?</span></h2>
      <div className="mt-8 grid gap-8 sm:mt-12 lg:grid-cols-2 lg:gap-14"><div className="max-w-lg space-y-4 text-[14px] leading-6 text-[#46514b] sm:space-y-6 sm:text-[16px] sm:leading-8"><p>{c.problem}</p><p>{c.solution}</p></div><ul className="grid grid-cols-1 gap-3 min-[390px]:grid-cols-2 lg:grid-cols-1 lg:gap-5">{points.map((point) => <li key={point} className="flex items-center gap-2.5 rounded-xl bg-[#f5f8f6] px-3 py-3 text-[12px] text-[#46514b] sm:bg-transparent sm:px-0 sm:py-0 sm:text-[15px]"><Check size={16} className="shrink-0 text-[#258c53]" />{point}</li>)}</ul></div>
      <p className="mt-8 max-w-2xl border-t border-zinc-200 pt-6 text-[14px] leading-6 text-[#343b37] sm:mt-14 sm:pt-8 sm:text-[16px] sm:leading-7">{c.conclusion}</p>
    </div></section>
  );
}

function ValueSection() {
  const { language } = useLanguage(); const c = landingCopy[language]; const items = language === "pt" ? benefits : translatedData[language].benefits;
  return (
    <section className="bg-[#303a35] py-14 text-white sm:py-20 lg:py-28"><div className="mx-auto max-w-6xl px-4 sm:px-8 lg:px-0">
      <p className="text-[10px] font-bold uppercase tracking-[.14em] text-[#78d6a3] sm:text-[11px]">{c.results}</p><h2 className="mt-3 max-w-2xl text-[32px] font-light leading-tight sm:mt-4 sm:text-4xl lg:text-5xl">{c.helps} <span className="italic text-[#78d6a3]">{c.business}</span></h2>
      <div className="mt-8 grid grid-cols-2 gap-2.5 sm:mt-12 sm:gap-5 md:grid-cols-2 lg:grid-cols-3">{items.map((item) => <article key={item.title} className="rounded-2xl border border-white/15 p-4 sm:min-h-48 sm:p-7"><item.icon size={21} strokeWidth={1.6} className="text-[#78d6a3] sm:h-[25px] sm:w-[25px]" /><h3 className="mt-3 text-[13px] font-semibold sm:mt-6 sm:text-[16px]">{item.title}</h3><p className="mt-2 text-[11px] leading-[1.55] text-zinc-300 sm:mt-3 sm:text-[13px] sm:leading-6">{item.text}</p></article>)}</div>
    </div></section>
  );
}

function Resources() {
  const { language } = useLanguage(); const c = landingCopy[language]; const items = language === "pt" ? areas : translatedData[language].areas; const [active, setActive] = useState(0); const area = items[active];
  return (
    <section id="recursos" className="bg-white py-14 sm:py-20 lg:py-28"><div className="mx-auto max-w-6xl px-4 sm:px-8 lg:px-0">
      <h2 className="max-w-md text-[32px] font-light leading-tight text-[#343b37] sm:text-4xl lg:text-5xl">{c.essential} <span className="italic text-[#258c53]">{c.nonstop}</span></h2>
      <div className="mt-8 grid items-center gap-6 sm:mt-12 sm:gap-10 lg:grid-cols-[.7fr_1.3fr]"><div className="landing-scrollbar-none flex snap-x gap-2 overflow-x-auto pb-1 lg:block lg:space-y-1 lg:overflow-visible lg:border-y lg:border-zinc-200 lg:py-2">{items.map((item, index) => <button key={item.label} type="button" onClick={() => setActive(index)} className={`flex shrink-0 snap-start items-center justify-between rounded-full border px-4 py-2.5 text-left text-[13px] lg:w-full lg:rounded-none lg:border-x-0 lg:border-t-0 lg:border-zinc-100 lg:px-2 lg:py-5 lg:text-[15px] lg:last:border-0 ${active === index ? "border-[#258c53] bg-[#edf7f1] font-semibold text-[#258c53] lg:bg-transparent" : "border-zinc-200 text-[#4b5650]"}`}>{item.label}<span className="ml-5 hidden text-xl font-light lg:block">{active === index ? "−" : "+"}</span></button>)}</div>
        <div className="rounded-2xl bg-[#f3f6f4] p-3 sm:p-8 lg:p-10"><div className="relative aspect-[16/10] overflow-hidden rounded-xl bg-white"><Image key={area.image} src={area.image} alt={area.imageAlt} fill className="object-contain object-center" sizes="(max-width: 1024px) 100vw, 60vw" /></div><div className="mx-auto max-w-xl px-2 pt-5 text-left sm:pt-8 sm:text-center"><area.icon size={22} className="text-[#258c53] sm:mx-auto sm:h-6 sm:w-6" /><h3 className="mt-3 text-[17px] font-semibold text-[#2f3833] sm:mt-4 sm:text-xl">{area.title}</h3><p className="mt-2 text-[12px] leading-5 text-[#6a7770] sm:mt-3 sm:text-sm sm:leading-6">{area.text}</p></div></div>
      </div>
    </div></section>
  );
}

function MobileSection() {
  const { language } = useLanguage();
  const copy = {
    pt: { badge: "Também no celular", title: "Acompanhe a operação mesmo longe do computador", text: "O computador continua sendo o ambiente principal para trabalhar com mais espaço. No celular, as telas se reorganizam para você consultar, cadastrar e resolver o que precisar sem perder recursos.", items: ["Dashboard e resultados", "Vendas, produtos e estoque", "Caixa, clientes e empresas"], action: "Acessar minha conta" },
    en: { badge: "Also on mobile", title: "Keep up with your operation away from the computer", text: "Desktop remains the main workspace. On mobile, screens reorganize so you can review, add and manage what you need without losing access to features.", items: ["Dashboard and results", "Sales, products and inventory", "Cash, customers and companies"], action: "Access my account" },
    es: { badge: "También en móvil", title: "Acompaña la operación lejos de la computadora", text: "La computadora sigue siendo el espacio principal. En el móvil, las pantallas se reorganizan para consultar, registrar y resolver lo necesario sin perder recursos.", items: ["Panel y resultados", "Ventas, productos e inventario", "Caja, clientes y empresas"], action: "Acceder a mi cuenta" },
  }[language];

  return (
    <section className="overflow-hidden bg-[#f1f6f3] py-14 sm:py-20 lg:py-28">
      <div className="mx-auto grid max-w-6xl items-center gap-8 px-4 sm:px-8 md:grid-cols-[.9fr_1.1fr] lg:gap-20 lg:px-0">
        <div className="order-2 md:order-1">
          <p className="text-[10px] font-bold uppercase tracking-[.15em] text-[#258c53] sm:text-[11px]">{copy.badge}</p>
          <h2 className="mt-3 max-w-xl text-[31px] font-light leading-[1.1] text-[#26312b] sm:text-4xl lg:text-5xl">{copy.title}</h2>
          <p className="mt-4 max-w-xl text-[13px] leading-6 text-[#5c6a63] sm:mt-6 sm:text-[15px] sm:leading-7">{copy.text}</p>
          <ul className="mt-5 grid gap-2 sm:mt-7 sm:grid-cols-2">
            {copy.items.map((item) => <li key={item} className="flex items-center gap-2 rounded-xl border border-[#dce7e1] bg-white px-3 py-3 text-[12px] font-medium text-[#35433c]"><Check size={15} className="shrink-0 text-[#16834c]" />{item}</li>)}
          </ul>
          <Link href="/auth/login" className="mt-6 inline-flex h-12 w-full items-center justify-center gap-3 rounded-full bg-[#087f47] px-6 text-[11px] font-bold uppercase tracking-[.07em] text-white sm:mt-8 sm:w-auto">{copy.action}<ArrowRight size={16} /></Link>
        </div>
        <div className="order-1 mx-auto w-full max-w-[430px] md:order-2">
          <div className="relative mx-auto w-[62%] min-w-[210px] max-w-[280px] rounded-[34px] border-[8px] border-[#17231d] bg-[#17231d] p-1 shadow-[0_30px_65px_rgba(35,70,51,.22)]">
            <span className="absolute left-1/2 top-2 z-10 h-1.5 w-14 -translate-x-1/2 rounded-full bg-black/80" />
            <div className="relative aspect-[9/17] overflow-hidden rounded-[23px] bg-white"><Image src="/images/landing/gevyro-mobile-dashboard-clean.webp" alt="Dashboard responsivo da Gevyro em um celular" fill className="object-cover object-top" sizes="280px" /></div>
          </div>
        </div>
      </div>
    </section>
  );
}

function PlansLegacy() {
  const { language } = useLanguage(); const c = landingCopy[language]; const items = language === "pt" ? plans : translatedData[language].plans;
  return (
    <section id="planos" className="bg-[#303a35] py-24 text-white sm:py-32"><div className="mx-auto max-w-6xl px-5 lg:px-0">
      <div className="text-center"><p className="text-[11px] font-bold uppercase tracking-[.14em] text-[#78d6a3]">{c.plans}</p><h2 className="mt-4 text-4xl font-light sm:text-5xl">{c.choose} <span className="italic text-[#78d6a3]">{c.operation}</span></h2></div>
      <div className="mt-16 grid gap-5 md:grid-cols-2 lg:grid-cols-4">{plans.map((plan) => <article key={plan.name} className={`flex min-h-[380px] flex-col rounded-2xl border p-7 ${plan.featured ? "border-[#78d6a3] bg-white text-[#2f3833]" : "border-white/15"}`}><div className="flex items-center justify-between"><h3 className="text-2xl font-semibold">{plan.name}</h3>{plan.featured && <span className="text-[9px] font-bold uppercase tracking-wider text-[#258c53]">Recomendado</span>}</div><p className={`mt-4 text-sm ${plan.featured ? "text-[#66736c]" : "text-zinc-300"}`}>{plan.period}</p><p className={`mt-2 text-[13px] ${plan.featured ? "text-[#66736c]" : "text-zinc-400"}`}>{plan.limits}</p><ul className="mt-8 flex-1 space-y-4">{plan.features.map((feature) => <li key={feature} className="flex gap-3 text-[13px]"><Check size={16} className="shrink-0 text-[#78d6a3]" />{feature}</li>)}</ul><Link href="/auth/cadastro" className={`mt-8 rounded-full px-5 py-3 text-center text-[11px] font-bold uppercase tracking-wider ${plan.featured ? "bg-[#258c53] text-white" : "border border-white/30 hover:border-[#78d6a3]"}`}>Escolher plano</Link></article>)}</div>
    </div></section>
  );
}

function FAQLegacy() {
  const [open, setOpen] = useState(-1);
  return (
    <section id="faq" className="bg-white py-24 sm:py-32"><div className="mx-auto max-w-6xl px-5 lg:px-0"><h2 className="text-4xl font-light text-[#343b37] sm:text-5xl">Perguntas <span className="italic text-[#258c53]">frequentes</span></h2><div className="mt-16">{faqs.map((item, index) => <div key={item.q} className="border-b border-zinc-200"><button type="button" onClick={() => setOpen(open === index ? -1 : index)} className="flex w-full items-center justify-between gap-6 py-6 text-left text-[15px] font-semibold text-[#343b37]">{item.q}<ChevronDown size={18} className={`shrink-0 text-[#258c53] transition-transform ${open === index ? "rotate-180" : ""}`} /></button>{open === index && <p className="max-w-3xl pb-6 text-sm leading-7 text-[#66736c]">{item.a}</p>}</div>)}</div></div></section>
  );
}

function FooterLegacy() {
  return <><section className="bg-[#303a35] py-20 text-white"><div className="mx-auto flex max-w-6xl flex-col justify-between gap-8 px-5 md:flex-row md:items-center lg:px-0"><div><h2 className="text-4xl font-light">Comece a organizar <span className="italic text-[#78d6a3]">sua loja</span></h2><p className="mt-4 text-sm text-zinc-300">Teste o Gevyro e conheça a rotina do sistema.</p></div><Link href="/auth/cadastro" className="inline-flex items-center justify-center gap-3 rounded-full bg-[#78d6a3] px-7 py-4 text-[11px] font-bold uppercase tracking-wider text-[#173323]">Criar minha conta <ArrowRight size={16} /></Link></div></section><footer className="bg-white py-14 text-[#3f4944]"><div className="mx-auto grid max-w-6xl gap-10 px-5 sm:grid-cols-3 lg:px-0"><div><Image src="/images/gevyro-logo-400.webp" alt="Gevyro" width={400} height={145} className="h-auto w-[200px] object-contain" /><p className="mt-4 text-sm text-[#718078]">Gestão em evolução.</p><p className="mt-3 text-xs text-[#8a958f]">CNPJ 68.259.534/0001-70</p></div><div><h3 className="font-semibold">Atendimento</h3><div className="mt-4 flex flex-col gap-3 text-sm text-[#718078]"><Link href="/como-usar">Como usar</Link><Link href="/contato">Fale conosco</Link><Link href="/auth/login">Área do cliente</Link></div></div><div><h3 className="font-semibold">Transparência</h3><div className="mt-4 flex flex-col gap-3 text-sm text-[#718078]"><Link href="/termos">Termos de uso</Link><Link href="/privacidade">Política de privacidade</Link><Link href="/contato">Suporte</Link></div></div></div><div className="mx-auto mt-12 flex max-w-6xl flex-col items-center justify-between gap-3 border-t border-zinc-200 px-5 pt-6 text-xs text-[#8a958f] sm:flex-row lg:px-0"><span>© 2026 Gevyro. Todos os direitos reservados.</span><span>Plataforma de gestão desenvolvida no Brasil.</span></div></footer></>;
}

function PlansSection() {
  const { language } = useLanguage(); const c = landingCopy[language]; const items = language === "pt" ? plans : translatedData[language].plans;
  return <section id="planos" className="bg-[#303a35] py-14 text-white sm:py-20 lg:py-28"><div className="mx-auto max-w-6xl px-4 sm:px-8 lg:px-0"><div className="text-center"><p className="text-[10px] font-bold uppercase tracking-[.14em] text-[#78d6a3] sm:text-[11px]">{c.plans}</p><h2 className="mt-3 text-[32px] font-light leading-tight sm:mt-4 sm:text-4xl lg:text-5xl">{c.choose} <span className="italic text-[#78d6a3]">{c.operation}</span></h2></div><div className="landing-scrollbar-none -mx-4 mt-8 flex snap-x snap-mandatory gap-3 overflow-x-auto px-4 pb-3 sm:mx-0 sm:mt-12 sm:grid sm:grid-cols-2 sm:overflow-visible sm:px-0 sm:pb-0 lg:grid-cols-4">{items.map(plan=><article key={plan.name} className={`flex min-h-[330px] w-[82vw] max-w-[330px] shrink-0 snap-center flex-col rounded-2xl border p-5 sm:min-h-[360px] sm:w-auto sm:max-w-none sm:p-7 ${plan.featured?"border-[#78d6a3] bg-white text-[#2f3833]":"border-white/15"}`}><div className="flex items-center justify-between gap-3"><h3 className="text-xl font-semibold sm:text-2xl">{plan.name}</h3>{plan.featured&&<span className="text-[8px] font-bold uppercase tracking-wider text-[#258c53] sm:text-[9px]">{c.recommended}</span>}</div><p className="mt-3 text-[12px] opacity-75 sm:mt-4 sm:text-sm">{plan.period}</p><p className="mt-1.5 text-[12px] opacity-70 sm:mt-2 sm:text-[13px]">{plan.limits}</p><ul className="mt-6 flex-1 space-y-3 sm:mt-8 sm:space-y-4">{plan.features.map(feature=><li key={feature} className="flex gap-2.5 text-[12px] sm:text-[13px]"><Check size={15} className="shrink-0 text-[#78d6a3]"/>{feature}</li>)}</ul><Link href="/auth/cadastro" className={`mt-6 rounded-full px-5 py-3 text-center text-[10px] font-bold uppercase tracking-wider sm:mt-8 sm:text-[11px] ${plan.featured?"bg-[#258c53] text-white":"border border-white/30"}`}>{c.choosePlan}</Link></article>)}</div><p className="mt-2 text-center text-[10px] text-white/45 sm:hidden">Deslize para comparar os planos</p></div></section>;
}

function FAQSection() {
  const { language } = useLanguage(); const c = landingCopy[language]; const items = language === "pt" ? faqs : translatedData[language].faqs; const [open,setOpen]=useState(-1);
  return <section id="faq" className="bg-white py-14 sm:py-20 lg:py-28"><div className="mx-auto max-w-6xl px-4 sm:px-8 lg:px-0"><h2 className="text-[32px] font-light leading-tight text-[#343b37] sm:text-4xl lg:text-5xl">{c.questions} <span className="italic text-[#258c53]">{c.frequent}</span></h2><div className="mt-8 sm:mt-12">{items.map((item,index)=><div key={item.q} className="border-b border-zinc-200"><button type="button" onClick={()=>setOpen(open===index?-1:index)} className="flex w-full items-center justify-between gap-4 py-4 text-left text-[13px] font-semibold leading-5 text-[#343b37] sm:gap-6 sm:py-6 sm:text-[15px]">{item.q}<ChevronDown size={17} className={`shrink-0 text-[#258c53] transition-transform ${open===index?"rotate-180":""}`}/></button>{open===index&&<p className="max-w-3xl pb-4 text-[12px] leading-6 text-[#66736c] sm:pb-6 sm:text-sm sm:leading-7">{item.a}</p>}</div>)}</div></div></section>;
}

function LocalizedFooter() {
  const { language } = useLanguage(); const c=landingCopy[language];
  return <><section className="bg-[#303a35] py-12 text-white sm:py-16 lg:py-20"><div className="mx-auto flex max-w-6xl flex-col justify-between gap-6 px-4 sm:px-8 md:flex-row md:items-center lg:px-0"><div><h2 className="text-[30px] font-light leading-tight sm:text-4xl">{c.organize} <span className="italic text-[#78d6a3]">{c.store}</span></h2><p className="mt-3 text-[12px] text-zinc-300 sm:mt-4 sm:text-sm">{c.test}</p></div><Link href="/auth/cadastro" className="rounded-full bg-[#78d6a3] px-7 py-3.5 text-center text-[10px] font-bold uppercase text-[#173323] sm:py-4 sm:text-[11px]">{c.create}</Link></div></section><footer className="bg-white py-10 text-[#3f4944] sm:py-14"><div className="mx-auto grid max-w-6xl grid-cols-2 gap-x-6 gap-y-8 px-4 sm:grid-cols-3 sm:px-8 lg:px-0"><div className="col-span-2 sm:col-span-1"><Image src="/images/gevyro-logo-400.webp" alt="Gevyro" width={400} height={145} className="h-auto w-[150px] sm:w-[200px]"/><p className="mt-3 text-[12px] text-[#718078] sm:mt-4 sm:text-sm">Gestão em evolução.</p><p className="mt-2 text-[10px] text-[#8a958f] sm:mt-3 sm:text-xs">CNPJ 68.259.534/0001-70</p></div><div><h3 className="text-[13px] font-semibold sm:text-base">{c.service}</h3><div className="mt-3 flex flex-col gap-2.5 text-[12px] text-[#718078] sm:mt-4 sm:gap-3 sm:text-sm"><Link href="/como-usar">{c.how}</Link><Link href="/contato">{c.contact}</Link><Link href="/auth/login">{c.client}</Link></div></div><div><h3 className="text-[13px] font-semibold sm:text-base">{c.transparency}</h3><div className="mt-3 flex flex-col gap-2.5 text-[12px] text-[#718078] sm:mt-4 sm:gap-3 sm:text-sm"><Link href="/termos">{c.terms}</Link><Link href="/privacidade">{c.privacy}</Link><Link href="/contato">{c.support}</Link></div></div></div><div className="mx-auto mt-8 flex max-w-6xl flex-col justify-between gap-2 border-t border-zinc-200 px-4 pt-5 text-[10px] text-[#8a958f] sm:mt-12 sm:flex-row sm:px-8 sm:pt-6 sm:text-xs lg:px-0"><span>© 2026 Gevyro. {c.rights}</span><span>{c.made}</span></div></footer></>;
}

const installCopy = {
  pt: { badge: "Gevyro no seu dispositivo", title: "Instale agora e leve sua gestão para qualquer lugar", text: "Acesse vendas, estoque, pedidos e resultados direto pela tela inicial, com a experiência de um aplicativo e sem ocupar espaço desnecessário.", install: "Instalar aplicativo", browser: "Usar no navegador", play: "Google Play", soon: "Procurar Gevyro na", detail: "Disponível para celular, tablet e computador" },
  en: { badge: "Gevyro on your device", title: "Install now and manage your business anywhere", text: "Access sales, inventory, orders and results from your home screen, with an app-like experience and no unnecessary storage usage.", install: "Install app", browser: "Use in browser", play: "Google Play", soon: "Find Gevyro on", detail: "Available on mobile, tablet and desktop" },
  es: { badge: "Gevyro en tu dispositivo", title: "Instala ahora y lleva tu gestión a cualquier lugar", text: "Accede a ventas, inventario, pedidos y resultados desde la pantalla de inicio, con experiencia de aplicación y sin ocupar espacio innecesario.", install: "Instalar aplicación", browser: "Usar en el navegador", play: "Google Play", soon: "Buscar Gevyro en", detail: "Disponible para móvil, tableta y computadora" },
};

function InstallSection() {
  const { language } = useLanguage(); const c = installCopy[language];
  const requestInstall = () => window.dispatchEvent(new Event("gevyro:install-request"));
  return <section className="bg-white px-3 py-14 sm:px-6 sm:py-20 lg:py-28"><div className="relative mx-auto grid max-w-6xl overflow-hidden rounded-[24px] bg-[#075b48] px-5 py-9 text-white shadow-[0_28px_80px_rgba(7,91,72,.18)] sm:rounded-[32px] sm:px-10 sm:py-12 lg:min-h-[470px] lg:grid-cols-[.85fr_1.15fr] lg:items-center lg:px-14">
    <div className="pointer-events-none absolute inset-0 opacity-40" style={{backgroundImage:"radial-gradient(circle at 80% 20%, rgba(120,214,163,.4), transparent 30%), linear-gradient(120deg, transparent 45%, rgba(255,255,255,.06))"}}/>
    <div className="relative z-10 max-w-xl"><p className="text-[10px] font-bold uppercase tracking-[.16em] text-[#9be5ba] sm:text-[11px]">{c.badge}</p><h2 className="mt-4 text-[31px] font-semibold italic leading-[1.08] tracking-[-.035em] sm:mt-5 sm:text-5xl">{c.title}</h2><p className="mt-4 max-w-lg text-[12px] leading-6 text-emerald-50/80 sm:mt-5 sm:text-sm sm:leading-7">{c.text}</p><div className="mt-6 grid grid-cols-2 gap-2 sm:mt-8 sm:flex sm:flex-wrap sm:gap-3"><button type="button" onClick={requestInstall} className="col-span-2 inline-flex items-center justify-center gap-2 rounded-xl bg-white px-4 py-3 text-[12px] font-bold text-[#075b48] shadow-lg transition hover:-translate-y-0.5 sm:col-auto sm:gap-3 sm:px-5 sm:py-3.5 sm:text-sm"><Download size={18}/>{c.install}</button><a href="https://play.google.com/store/search?q=Gevyro&c=apps" target="_blank" rel="noopener noreferrer" aria-label="Procurar Gevyro na Google Play" className="inline-flex items-center justify-center gap-2 rounded-xl bg-black px-3 py-2.5 text-left text-white transition hover:-translate-y-0.5 hover:bg-zinc-900 sm:gap-3 sm:px-4"><svg width="19" height="22" viewBox="0 0 24 27" aria-hidden><path fill="#00d6ff" d="M1 1.3 13.9 13.5 1 25.7z"/><path fill="#ffd400" d="m13.9 13.5 4-3.8 4.5 2.5c1.2.7 1.2 1.9 0 2.6l-4.5 2.5z"/><path fill="#ff3a44" d="M1 25.7 17.9 17.3l-4-3.8z"/><path fill="#00ef76" d="M1 1.3 17.9 9.7l-4 3.8z"/></svg><span><small className="block text-[7px] font-medium uppercase tracking-wide text-zinc-400 sm:text-[9px]">{c.soon}</small><strong className="block text-[11px] leading-tight sm:text-sm">{c.play}</strong></span></a><Link href="/auth/login" className="inline-flex items-center justify-center gap-2 rounded-xl border border-white/25 bg-white/10 px-3 py-3 text-[11px] font-bold text-white transition hover:bg-white/15 sm:gap-3 sm:px-5 sm:py-3.5 sm:text-sm"><Globe2 size={17}/>{c.browser}</Link></div><p className="mt-4 flex items-center gap-2 text-[10px] text-emerald-100/70 sm:mt-5 sm:text-xs"><Smartphone size={14}/>{c.detail}</p></div>
    <div className="relative z-10 mt-8 min-h-[215px] sm:mt-12 sm:min-h-[300px] lg:mt-0 lg:min-h-[380px]"><div className="absolute left-[2%] top-[8%] w-[88%] rotate-[-2deg] rounded-[13px] border-[5px] border-[#17231d] bg-[#17231d] shadow-2xl sm:rounded-[18px] sm:border-[7px]"><div className="relative aspect-[16/10] overflow-hidden rounded-[7px] sm:rounded-[10px]"><Image src="/images/landing/gevyro-feature-vendas.avif" alt="Painel Gevyro instalado no computador" fill className="object-cover object-top" sizes="(max-width: 1024px) 90vw, 50vw"/></div><div className="mx-auto h-1.5 w-16 rounded-b-full bg-zinc-600 sm:h-2 sm:w-24"/></div><div className="absolute bottom-0 right-[2%] w-[28%] rotate-[3deg] rounded-[18px] border-[5px] border-[#17231d] bg-white p-1 shadow-2xl sm:rounded-[24px] sm:border-[7px]"><div className="relative aspect-[9/17] overflow-hidden rounded-[11px] bg-[#eff6f2] sm:rounded-[15px]"><Image src="/images/landing/gevyro-mobile-dashboard-clean.webp" alt="Dashboard móvel do Gevyro sem elementos de desenvolvimento" fill className="object-cover object-top" sizes="25vw"/></div></div></div>
  </div></section>;
}

export default function Home() {
  return <div className="min-h-screen bg-white font-sans"><script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(structuredData) }} /><Header /><main><Hero /><About /><ValueSection /><Resources /><MobileSection /><PlansSection /><FAQSection /><InstallSection /></main><LocalizedFooter /><WhatsAppFloat /></div>;
}
