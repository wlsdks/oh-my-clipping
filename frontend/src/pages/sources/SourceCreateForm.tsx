import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Form, FormField, FormItem, FormLabel, FormControl, FormMessage } from "@/components/ui/form";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { SourceDiscoverInput } from "./SourceDiscoverInput";
import type { SourceCreateRequest } from "@/services/sourceService";
import type { Category } from "@/types/category";
import { adminInputSchemas } from "@/shared/lib/inputSchemas";

const formSchema = z.object({
  // 공용 inputSchemas로 BE `AdminSourceService` 상한과 동기화한다.
  name: adminInputSchemas.sourceName,
  url: adminInputSchemas.sourceUrl,
  categoryId: z.string().min(1, "주제를 선택하세요"),
  sourceRegion: z.enum(["GLOBAL", "DOMESTIC", "UNKNOWN"]),
  emoji: z.string().trim().max(10, "이모지는 최대 10자까지 입력할 수 있어요").optional()
});

type FormValues = z.infer<typeof formSchema>;

interface SourceCreateFormProps {
  categories: Category[];
  onSubmit: (data: SourceCreateRequest) => void;
  isPending?: boolean;
}

export function SourceCreateForm({ categories, onSubmit, isPending }: SourceCreateFormProps) {
  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: "",
      url: "",
      categoryId: "",
      sourceRegion: "UNKNOWN",
      emoji: ""
    }
  });

  function handleSubmit(values: FormValues) {
    onSubmit({
      name: values.name,
      url: values.url,
      categoryId: values.categoryId,
      sourceRegion: values.sourceRegion,
      emoji: values.emoji || null
    });
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
        <div className="space-y-1.5">
          <Label className="text-sm font-medium">소스 검색 (선택)</Label>
          <SourceDiscoverInput
            onSelect={(result) => {
              if (result.name) form.setValue("name", result.name);
              if (result.url) form.setValue("url", result.url);
              if (result.region === "DOMESTIC") form.setValue("sourceRegion", "DOMESTIC");
              else if (result.region === "GLOBAL") form.setValue("sourceRegion", "GLOBAL");
            }}
          />
          <p className="text-xs text-muted-foreground">
            이름이나 URL로 검색해 자동 완성하거나, 아래 필드에 직접 입력하세요.
          </p>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <FormField
            control={form.control}
            name="name"
            render={({ field }) => (
              <FormItem>
                <FormLabel>소스 이름</FormLabel>
                <FormControl>
                  <Input placeholder="예: TechCrunch" aria-required="true" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="emoji"
            render={({ field }) => (
              <FormItem>
                <FormLabel>이모지 (선택)</FormLabel>
                <FormControl>
                  <Input placeholder="예: 📰" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <FormField
          control={form.control}
          name="url"
          render={({ field }) => (
            <FormItem>
              <FormLabel>뉴스 피드 주소</FormLabel>
              <FormControl>
                <Input placeholder="https://example.com/rss" aria-required="true" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <div className="grid grid-cols-2 gap-4">
          <FormField
            control={form.control}
            name="categoryId"
            render={({ field }) => (
              <FormItem>
                <FormLabel>주제</FormLabel>
                <Select onValueChange={field.onChange} value={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="주제 선택" />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {categories.map((cat) => (
                      <SelectItem key={cat.id} value={cat.id}>
                        {cat.name}
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
            name="sourceRegion"
            render={({ field }) => (
              <FormItem>
                <FormLabel>지역</FormLabel>
                <Select onValueChange={field.onChange} value={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    <SelectItem value="DOMESTIC">국내</SelectItem>
                    <SelectItem value="GLOBAL">해외</SelectItem>
                    <SelectItem value="UNKNOWN">미분류</SelectItem>
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <Button type="submit" disabled={isPending} className="w-full">
          {isPending ? "등록 중..." : "소스 등록"}
        </Button>
      </form>
    </Form>
  );
}
