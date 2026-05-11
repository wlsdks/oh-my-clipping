import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { userKeys } from "@/queries/userKeys";
import { userService } from "@/services/userService";
import type { CategoryBrowseItem } from "@/services/userService";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { EmptyState } from "@/components/shared/EmptyState";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { matchesKoreanSearch } from "@/utils/search";
import { formatSlackDestinationLabel } from "@/shared/lib/slackChannel";
import { useAuthStore } from "@/store/authStore";
import { SlackConnectModal } from "@/components/shared/SlackConnectModal";

function hourLabel(h: number): string {
  if (h < 12) return `오전 ${h}시`;
  if (h === 12) return "오후 12시";
  return `오후 ${h - 12}시`;
}

function matchesSearch(item: CategoryBrowseItem, query: string): boolean {
  if (!query.trim()) return true;
  if (matchesKoreanSearch(item.name, query)) return true;
  if (item.description && matchesKoreanSearch(item.description, query)) return true;
  return false;
}

export function CategoryBrowsePage() {
  const qc = useQueryClient();
  const [search, setSearch] = useState("");
  const [subscribingId, setSubscribingId] = useState<string | null>(null);
  const [slackModalCategoryId, setSlackModalCategoryId] = useState<string | null>(null);
  const [isConnecting, setIsConnecting] = useState(false);

  const user = useAuthStore((s) => s.user);

  const { data: categories = [], isLoading, isError, refetch } = useQuery({
    queryKey: userKeys.categoryBrowse(),
    queryFn: () => userService.browseCategories()
  });

  const { data: requests = [] } = useQuery({
    queryKey: userKeys.clippingRequests(),
    queryFn: () => userService.listClippingRequests()
  });
  const subscriptionCount = requests.filter((r) => r.status === "APPROVED" || r.status === "PENDING").length;
  const isAtLimit = subscriptionCount >= 5;

  const { mutate: subscribeDm } = useMutation({
    mutationFn: (categoryId: string) => {
      setSubscribingId(categoryId);
      return userService.subscribeCategoryDm(categoryId);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: userKeys.categoryBrowse() });
      qc.invalidateQueries({ queryKey: userKeys.clippingRequests() });
      toast.success("DM 구독이 설정됐어요");
      setSubscribingId(null);
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err, "구독에 실패했어요"));
      setSubscribingId(null);
    }
  });

  const handleSubscribeClick = (categoryId: string) => {
    if (user?.hasSlackDm === true) {
      subscribeDm(categoryId);
    } else {
      setSlackModalCategoryId(categoryId);
    }
  };

  const handleSlackConnect = async (slackMemberId: string) => {
    if (!slackModalCategoryId) return;
    const targetCategoryId = slackModalCategoryId;
    setIsConnecting(true);
    try {
      await userService.updateSlackMemberId(slackMemberId);
      const currentUser = useAuthStore.getState().user;
      if (currentUser) {
        useAuthStore.getState().login({ ...currentUser, hasSlackDm: true });
      }
      setSlackModalCategoryId(null);
      subscribeDm(targetCategoryId);
    } catch (err) {
      toast.error(userFriendlyMessage(err, "Slack 연결에 실패했어요"));
    } finally {
      setIsConnecting(false);
    }
  };

  const filtered = categories.filter((c) => matchesSearch(c, search));

  if (isLoading) {
    return (
      <div className="p-8 space-y-3 animate-pulse">
        <div className="h-4 bg-muted rounded w-1/3" />
        <div className="h-4 bg-muted rounded w-2/3" />
        <div className="h-4 bg-muted rounded w-1/2" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="p-8">
        <EmptyState
          title="데이터를 불러올 수 없어요"
          description="네트워크 상태를 확인하고 다시 시도해주세요"
          action={<Button variant="outline" size="sm" onClick={() => refetch()}>다시 시도</Button>}
        />
      </div>
    );
  }

  return (
    <div className="p-4 sm:p-6 space-y-5">
      <div>
        <p className="text-xs text-muted-foreground">Clipping</p>
        <h1 className="text-2xl font-bold">구독 가능한 주제</h1>
        <p className="text-sm text-muted-foreground mt-1">관리자가 준비한 뉴스 주제예요. 원클릭으로 바로 구독할 수 있어요.</p>
      </div>

      {/* 이용 가이드 */}
      <div className="rounded-xl border border-primary/15 bg-primary/[0.03] p-4 space-y-2">
        <p className="text-sm font-semibold text-foreground">이렇게 이용하세요</p>
        <ol className="text-sm text-muted-foreground space-y-1 list-decimal list-inside">
          <li>관심 있는 주제를 찾아 <span className="font-medium text-primary">"DM으로 받기"</span>를 눌러주세요</li>
          <li>바로 구독이 시작되고, 설정된 시간에 Slack DM으로 뉴스 요약이 도착해요</li>
          <li>원하는 주제가 없다면 <span className="font-medium text-primary">"빠른 세팅"</span>에서 직접 만들 수도 있어요</li>
        </ol>
      </div>

      <Input
        placeholder="주제 검색 (초성 검색 지원)"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        className="max-w-md"
        aria-label="주제 검색"
      />

      {filtered.length === 0 ? (
        <EmptyState
          title={search ? "검색 결과가 없어요" : "아직 등록된 주제가 없어요"}
          description={search ? "다른 키워드로 검색해 보세요" : "관리자에게 문의하거나 새 주제를 만들어 보세요"}
        />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((cat) => (
            <CategoryCard
              key={cat.id}
              category={cat}
              onSubscribeDm={() => handleSubscribeClick(cat.id)}
              isSubscribing={subscribingId === cat.id}
              isAtLimit={isAtLimit}
            />
          ))}
        </div>
      )}

      <SlackConnectModal
        open={slackModalCategoryId !== null}
        onOpenChange={(open) => { if (!open) setSlackModalCategoryId(null); }}
        onSubmit={handleSlackConnect}
        isSubmitting={isConnecting}
      />
    </div>
  );
}

function CategoryCard({
  category: cat,
  onSubscribeDm,
  isSubscribing,
  isAtLimit
}: {
  category: CategoryBrowseItem;
  onSubscribeDm: () => void;
  isSubscribing: boolean;
  isAtLimit: boolean;
}) {
  return (
    <div className="rounded-xl border bg-card p-5 space-y-3 hover:shadow-sm transition-shadow">
      {/* 이름 */}
      <h3 className="text-sm font-semibold leading-snug">{cat.name}</h3>

      {/* 설명 — 최대 2줄 */}
      {cat.description && (
        <p className="text-sm text-muted-foreground line-clamp-2 leading-relaxed">{cat.description}</p>
      )}

      {/* 메타 칩스 */}
      <div className="flex flex-wrap gap-1.5">
        <span className="text-xs bg-muted rounded-full px-2.5 py-1">구독자 {cat.subscriberCount}명</span>
        {cat.deliveryHour != null && (
          <span className="text-xs bg-muted rounded-full px-2.5 py-1">매일 {hourLabel(cat.deliveryHour)}</span>
        )}
        <span className="text-xs bg-muted rounded-full px-2.5 py-1">최대 {cat.maxItems}건</span>
        {cat.slackChannelId && (
          <span className="text-xs bg-muted rounded-full px-2.5 py-1">{formatSlackDestinationLabel(cat.slackChannelId, { genericChannelLabel: "Slack 채널" })}</span>
        )}
      </div>

      {/* 채널 안내 */}
      {cat.slackChannelId && (
        <p className="text-xs text-muted-foreground">Slack에서 이 채널에 참여하면 채널에서도 볼 수 있어요</p>
      )}

      {/* DM 구독 버튼 */}
      {cat.isSubscribed ? (
        <span className="inline-flex items-center text-xs font-medium text-primary bg-primary/10 rounded-full px-3 py-1.5">
          DM 수신 중
        </span>
      ) : (
        <Button size="sm" onClick={() => isAtLimit ? toast("구독 한도(5개)에 도달했어요. 기존 구독을 해제한 후 다시 시도해 주세요.") : onSubscribeDm()} disabled={isSubscribing} className="rounded-full">
          {isSubscribing ? "구독 중..." : "DM으로 받기"}
        </Button>
      )}
    </div>
  );
}
