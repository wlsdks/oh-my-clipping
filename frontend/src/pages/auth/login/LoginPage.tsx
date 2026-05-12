import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useNavigate, Link } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { motion, AnimatePresence } from "framer-motion";
import { HelpCircle, MessageCircle, Key } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { authService } from "@/services/authService";
import { authStore } from "@/store/authStore";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { useDevLoginShortcuts } from "@/shared/lib/devLoginShortcuts";
import { ClippingLogo } from "@/components/shared/ClippingLogo";

const loginSchema = z.object({
  email: z.string().min(1, "아이디를 입력하세요"),
  password: z.string().min(1, "비밀번호를 입력하세요")
});

type LoginForm = z.infer<typeof loginSchema>;

const formVariants = {
  hidden: {},
  visible: {
    transition: {
      staggerChildren: 0.1,
      delayChildren: 0.3
    }
  }
};

const itemVariants = {
  hidden: { y: 16, opacity: 0 },
  visible: { y: 0, opacity: 1, transition: { duration: 0.4, ease: "easeOut" as const } }
};

const BRAND_FEATURES = [
  "AI 기반 뉴스 수집 및 요약",
  "맞춤형 카테고리 구독",
  "Slack 자동 브리핑 발송",
  "키워드 트렌드 분석",
];

export function LoginPage() {
  const navigate = useNavigate();
  const [showHelp, setShowHelp] = useState(false);
  const form = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: "",
      password: "",
    },
  });
  const { mutate: login, isPending } = useMutation({
    mutationFn: ({ email, password }: LoginForm) => authService.login(email, password),
    onSuccess: (user) => {
      authStore.getState().login(user);
      navigate(user.role === "USER" ? "/user" : "/admin", { replace: true });
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "로그인에 실패했어요"))
  });

  // 개발용 빠른 로그인 — 테스트 기간 중 비활성
  useDevLoginShortcuts();

  return (
    <div className="min-h-screen flex flex-col lg:flex-row bg-background">
      {/* Brand panel — visible on lg+ */}
      <div className="hidden lg:flex lg:w-[480px] flex-col items-center justify-center bg-sidebar text-white p-12 relative overflow-hidden">
        {/* Ambient glow */}
        <div
          className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-64 h-64 rounded-full pointer-events-none"
          style={{ background: "radial-gradient(circle, var(--clipping-glow-medium) 0%, transparent 70%)" }}
        />
        <motion.div
          initial={{ scale: 0.8, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ type: "spring", stiffness: 120, damping: 14 }}
          className="relative z-10 flex flex-col items-center text-center space-y-6"
        >
          <motion.div
            animate={{ y: [0, -2, 0] }}
            transition={{ duration: 4, ease: "easeInOut", repeat: Infinity }}
          >
            <ClippingLogo size={72} className="text-[var(--clipping-glow)]" />
          </motion.div>
          <div>
            <h1 className="text-3xl font-bold tracking-tight">Clipping</h1>
            <p className="text-base text-white/70 mt-1">중요한 뉴스를 비춰드립니다</p>
          </div>
          <ul className="space-y-2.5 text-sm text-white/80">
            {BRAND_FEATURES.map((f) => (
              <li key={f} className="flex items-center gap-2">
                <span className="text-[var(--clipping-glow)]">&#10022;</span>
                {f}
              </li>
            ))}
          </ul>
        </motion.div>
      </div>

      {/* Form panel */}
      <div className="flex-1 flex flex-col items-center justify-center px-4 py-12">
        <div className="w-full max-w-sm space-y-5 p-8 rounded-2xl border bg-card shadow-sm">
          {/* 수평 로고 */}
          <motion.div
            className="flex items-center gap-3"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: "easeOut" }}
          >
            <div
              className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-xl"
            >
              <ClippingLogo size={36} className="text-primary" />
            </div>
            <div>
              <div className="text-lg font-bold text-foreground leading-tight">Clipping</div>
              <div className="text-xs text-muted-foreground">당신의 뉴스를 밝히다</div>
            </div>
          </motion.div>

          {/* 통합 로그인 설명 */}
          <p className="text-sm text-muted-foreground leading-relaxed border-l-2 border-primary pl-3">
            중요한 뉴스를 모아 요약해 드리는 브리핑 서비스예요
            <br />
            관리자와 회원 모두 이 페이지에서 로그인하세요.
          </p>

          {/* 폼 */}
          <Form {...form}>
            <motion.form
              onSubmit={form.handleSubmit((data) => login(data))}
              className="space-y-4"
              variants={formVariants}
              initial="hidden"
              animate="visible"
            >
              <motion.div variants={itemVariants}>
                <FormField
                  control={form.control}
                  name="email"
                  render={({ field }) => (
                      <FormItem>
                        <FormLabel>아이디</FormLabel>
                        <FormControl>
                          <Input autoComplete="username" placeholder="아이디 입력" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                  )}
                />
              </motion.div>
              <motion.div variants={itemVariants}>
                <FormField
                  control={form.control}
                  name="password"
                  render={({ field }) => (
                      <FormItem>
                        <FormLabel>비밀번호</FormLabel>
                        <FormControl>
                          <Input
                            autoComplete="current-password"
                            type="password"
                            placeholder="비밀번호 입력"
                            {...field}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                  )}
                />
              </motion.div>
              <motion.div variants={itemVariants}>
                <Button type="submit" className="w-full" disabled={isPending}>
                  {isPending ? "로그인 중..." : "로그인"}
                </Button>
              </motion.div>
            </motion.form>
          </Form>

          {/* 개발용 빠른 로그인 — 테스트 기간 중 제거 */}

          {/* 놓치면 안 되는 뉴스 tagline */}
          <p className="text-center text-xs text-muted-foreground">
            놓치면 안 되는 뉴스, 매일 비춰드립니다
          </p>

          {/* 계정 도움 */}
          <div className="text-center">
            <button
              type="button"
              onClick={() => setShowHelp(!showHelp)}
              className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              <HelpCircle className="h-3.5 w-3.5" />
              계정을 잊으셨나요?
            </button>
          </div>

          <AnimatePresence>
            {showHelp && (
              <motion.div
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: "auto", opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                transition={{ duration: 0.2, ease: "easeOut" }}
                className="overflow-hidden"
              >
                <div className="bg-muted/50 rounded-xl p-4 space-y-3">
                  <div className="flex items-start gap-2.5">
                    <MessageCircle className="h-4 w-4 mt-0.5 text-muted-foreground shrink-0" />
                    <div>
                      <p className="text-sm font-medium">아이디(이메일)를 잊으셨나요?</p>
                      <p className="text-xs text-muted-foreground mt-0.5">
                        관리자에게 Slack으로 이름과 부서를 알려주시면 확인해 드립니다.
                      </p>
                    </div>
                  </div>
                  <div className="flex items-start gap-2.5">
                    <Key className="h-4 w-4 mt-0.5 text-muted-foreground shrink-0" />
                    <div>
                      <p className="text-sm font-medium">비밀번호를 잊으셨나요?</p>
                      <p className="text-xs text-muted-foreground mt-0.5">
                        관리자에게 Slack으로 비밀번호 초기화를 요청해 주세요.
                        임시 비밀번호가 Slack DM 또는 관리자를 통해 전달됩니다.
                      </p>
                    </div>
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          {/* 회원가입 링크 */}
          <p className="text-center text-sm text-muted-foreground">
            계정이 없으신가요?{" "}
            <Link to="/signup" className="text-primary font-medium hover:underline">
              회원가입
            </Link>
          </p>

          {/* 데이터 수집 고지 */}
          <p className="text-[10px] text-muted-foreground text-center mt-4">
            서비스 개선을 위해 사용 패턴을 수집합니다
          </p>
        </div>
        <p className="mt-6 text-xs text-muted-foreground">&copy; 2026 Aslan. All rights reserved.</p>
      </div>
    </div>
  );
}
