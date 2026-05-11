import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useNavigate, Link } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { authService } from "@/services/authService";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { passwordSchema } from "@/shared/lib/passwordSchema";
import { useDepartmentTree } from "@/hooks/useDepartmentTree";

const signupSchema = z
  .object({
    username: z
      .string()
      .min(1, "이메일을 입력해주세요")
      .email("올바른 이메일 형식이 아니에요"),
    displayName: z.string().min(2, "이름은 2자 이상이어야 해요").max(20, "이름은 20자 이하여야 해요"),
    // 부서/팀 모두 선택 — 본부만 있는 조직, 팀 구분이 없는 조직(예: 인재경영실) 도 가입 가능해야 한다.
    departmentId: z.string().optional(),
    teamId: z.string().optional(),
    password: passwordSchema,
    confirmPassword: z.string().min(1, "비밀번호를 다시 입력하세요")
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "비밀번호가 일치하지 않아요",
    path: ["confirmPassword"]
  });

type SignupForm = z.infer<typeof signupSchema>;

export function SignupPage() {
  const navigate = useNavigate();
  const form = useForm<SignupForm>({
    resolver: zodResolver(signupSchema),
    defaultValues: {
      username: "",
      displayName: "",
      departmentId: "",
      teamId: "",
      password: "",
      confirmPassword: ""
    },
    mode: "onTouched",
    reValidateMode: "onChange"
  });

  // 공개 트리는 익명 호출이 가능하므로 signup 화면에서 바로 로드할 수 있다.
  const { departments, isLoading, isError, refetch } = useDepartmentTree();

  // cascade: 부서 선택 → 해당 부서의 팀 목록으로 teamId 선택 제한.
  const departmentId = form.watch("departmentId");
  const selectedDepartment = departments.find((d) => d.id === departmentId);
  const availableTeams = selectedDepartment?.teams ?? [];

  const { mutate: signup, isPending } = useMutation({
    mutationFn: ({ username, displayName, departmentId: deptId, teamId, password }: SignupForm) =>
      authService.signup({
        email: username,
        displayName,
        password,
        // 부서/팀 모두 선택 — 빈 값이면 null 로 보내 BE nullable 컬럼으로 저장한다.
        departmentId: deptId && deptId.length > 0 ? deptId : null,
        teamId: teamId && teamId.length > 0 ? teamId : null
      }),
    onSuccess: () => {
      toast.success("가입 신청이 완료됐어요. 관리자 승인 후 로그인할 수 있어요.");
      navigate("/login", { replace: true });
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "가입에 실패했어요"))
  });

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-background px-4">
      <div className="w-full max-w-sm space-y-5 p-8 rounded-2xl border bg-card shadow-sm">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => navigate("/login")}
            className="p-1.5 -ml-1.5 rounded-lg hover:bg-muted transition-colors text-muted-foreground hover:text-foreground"
            aria-label="로그인 페이지로 돌아가기"
          >
            <ArrowLeft size={18} />
          </button>
          <h1 className="text-xl font-bold">회원가입</h1>
        </div>

        {/* 부서/팀은 선택이므로 트리가 비어있어도 가입 자체는 막지 않는다.
            로드 에러는 안내만 남기고 submit 은 허용한다 (부서 없이도 진행 가능). */}
        {!isLoading && isError && (
          <div
            role="alert"
            data-testid="signup-tree-error"
            className="mb-4 rounded-lg border border-[var(--status-warning-text)]/30 bg-[var(--status-warning-bg)] p-3 text-sm text-[var(--status-warning-text)] flex items-center justify-between gap-2"
          >
            <span>부서 목록을 불러오지 못했어요. 부서 없이 가입할 수 있어요.</span>
            <button
              type="button"
              onClick={() => refetch()}
              className="text-xs underline hover:no-underline"
            >
              다시 시도
            </button>
          </div>
        )}

        <Form {...form}>
          <form onSubmit={form.handleSubmit((data) => signup(data))} className="space-y-4">
            <FormField
              control={form.control}
              name="username"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>이메일</FormLabel>
                  <FormControl>
                    <Input type="email" placeholder="name@company.com" autoComplete="email" aria-required="true" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="displayName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>이름</FormLabel>
                  <FormControl>
                    <Input placeholder="홍길동" aria-required="true" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="departmentId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>부서 (선택)</FormLabel>
                  <Select
                    onValueChange={(id) => {
                      field.onChange(id);
                      // 부서 변경 시 팀 선택은 초기화한다 (다른 부서의 팀을 들고 가지 않도록).
                      form.setValue("teamId", "", { shouldValidate: false });
                    }}
                    value={field.value ?? ""}
                    disabled={isLoading}
                  >
                    <FormControl>
                      <SelectTrigger aria-label="부서 선택">
                        <SelectValue placeholder={isLoading ? "부서를 불러오는 중..." : "부서 선택 (선택사항)"} />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {departments.map((dept) => (
                        <SelectItem key={dept.id} value={dept.id}>
                          {dept.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="teamId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>팀 (선택)</FormLabel>
                  <Select
                    onValueChange={field.onChange}
                    value={field.value ?? ""}
                    disabled={!departmentId || availableTeams.length === 0}
                  >
                    <FormControl>
                      <SelectTrigger aria-label="팀 선택">
                        <SelectValue
                          placeholder={
                            !departmentId
                              ? "먼저 부서를 선택하세요"
                              : availableTeams.length === 0
                                ? "이 부서에는 팀이 없어요"
                                : "팀 선택"
                          }
                        />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {availableTeams.map((team) => (
                        <SelectItem key={team.id} value={team.id}>
                          {team.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="password"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>비밀번호</FormLabel>
                  <FormControl>
                    <Input type="password" placeholder="영문 + 숫자 포함, 8자 이상" aria-required="true" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="confirmPassword"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>비밀번호 확인</FormLabel>
                  <FormControl>
                    <Input type="password" placeholder="비밀번호를 다시 입력하세요" aria-required="true" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            {Object.keys(form.formState.errors).length > 0 && (
              <div role="alert" className="rounded-lg bg-destructive/10 border border-destructive/20 p-3 text-sm text-destructive">
                입력 정보를 확인해주세요.
              </div>
            )}
            {/* 부서/팀은 선택이므로 목록이 비어있거나 로드 실패여도 submit 은 허용한다. */}
            <Button
              type="submit"
              className="w-full"
              disabled={isPending || isLoading}
            >
              {isPending ? "처리 중..." : "회원가입"}
            </Button>
          </form>
        </Form>

        <p className="text-center text-sm text-muted-foreground">
          이미 계정이 있으신가요?{" "}
          <Link to="/login" className="text-primary font-medium hover:underline">
            로그인
          </Link>
        </p>
      </div>
    </div>
  );
}
