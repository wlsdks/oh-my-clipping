import { useState, useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { userKeys } from "@/queries/userKeys";
import { userService } from "@/services/userService";
import type { DeliveryPreset } from "@/types/user";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";

const ALL_DAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"] as const;
const DAY_LABELS: Record<string, string> = {
  MON: "월",
  TUE: "화",
  WED: "수",
  THU: "목",
  FRI: "금",
  SAT: "토",
  SUN: "일"
};
const DELIVERY_SLOTS = [8, 12, 18] as const;

/** 슬롯에 없는 hour를 가장 가까운 슬롯으로 매핑한다. */
function nearestSlot(h: number): number {
  return DELIVERY_SLOTS.reduce((best, slot) => (Math.abs(slot - h) < Math.abs(best - h) ? slot : best));
}

function hourLabel(h: number): string {
  if (h === 0) return "자정";
  if (h < 12) return `오전 ${h}시`;
  if (h === 12) return "오후 12시";
  return `오후 ${h - 12}시`;
}

export function DeliveryScheduleSettings() {
  const qc = useQueryClient();

  const { data: schedule } = useQuery({
    queryKey: userKeys.deliverySchedule(),
    queryFn: () => userService.getDeliverySchedule()
  });

  const [preset, setPreset] = useState<DeliveryPreset>("WEEKDAYS");
  const [days, setDays] = useState<string[]>(["MON", "TUE", "WED", "THU", "FRI"]);
  const [hour, setHour] = useState(9);

  useEffect(() => {
    if (schedule) {
      setPreset(schedule.preset);
      setDays(schedule.deliveryDays);
      // 기존 hour가 슬롯에 없으면 가장 가까운 슬롯으로 매핑
      const slotHour = (DELIVERY_SLOTS as readonly number[]).includes(schedule.deliveryHour)
        ? schedule.deliveryHour
        : nearestSlot(schedule.deliveryHour);
      setHour(slotHour);
    }
  }, [schedule]);

  const { mutate: updateSchedule, isPending } = useMutation({
    mutationFn: () => userService.updateDeliverySchedule({ deliveryDays: days, deliveryHour: hour, preset }),
    onSuccess: (updated) => {
      qc.setQueryData(userKeys.deliverySchedule(), updated);
      toast.success("기본 발송 시간을 저장했어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "저장하지 못했어요"))
  });

  function handlePresetChange(p: DeliveryPreset) {
    setPreset(p);
    if (p === "WEEKDAYS") setDays(["MON", "TUE", "WED", "THU", "FRI"]);
    else if (p === "EVERYDAY") setDays([...ALL_DAYS]);
  }

  function handleSave() {
    if (preset === "CUSTOM" && days.length === 0) {
      toast.warning("최소 1개 요일을 선택해주세요");
      return;
    }
    updateSchedule();
  }

  const presetLabel =
    preset === "WEEKDAYS"
      ? "평일(월~금)"
      : preset === "EVERYDAY"
        ? "매일"
        : days.map((d) => DAY_LABELS[d] ?? d).join(", ");

  return (
    <div className="rounded-lg border bg-card p-5 space-y-5">
      <div>
        <h2 className="font-semibold">기본 발송 시간 설정</h2>
        <p className="text-xs text-muted-foreground mt-0.5">구독별 개별 설정이 없으면 이 시간에 발송돼요</p>
        {schedule && (
          <p className="text-xs text-muted-foreground mt-1">
            현재: {presetLabel} · {hourLabel(schedule.deliveryHour)}
          </p>
        )}
      </div>

      {/* 요일 프리셋 */}
      <div className="space-y-2">
        <Label>받고 싶은 요일</Label>
        <div className="flex flex-wrap gap-2">
          {(["WEEKDAYS", "EVERYDAY", "CUSTOM"] as DeliveryPreset[]).map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => handlePresetChange(p)}
              disabled={isPending}
              className={`rounded-full px-3 py-1 text-sm border transition-colors ${
                preset === p ? "bg-primary text-primary-foreground border-primary" : "border-border hover:bg-muted/50"
              }`}
            >
              {p === "WEEKDAYS" ? "평일만" : p === "EVERYDAY" ? "매일" : "직접 선택"}
            </button>
          ))}
        </div>
        {preset === "CUSTOM" && (
          <div className="flex gap-1.5 flex-wrap">
            {ALL_DAYS.map((d) => (
              <button
                key={d}
                type="button"
                onClick={() => {
                  setDays((prev) => (prev.includes(d) ? prev.filter((x) => x !== d) : [...prev, d]));
                }}
                disabled={isPending}
                className={`rounded-full w-9 h-9 text-sm border transition-colors ${
                  days.includes(d)
                    ? "bg-primary text-primary-foreground border-primary"
                    : "border-border hover:bg-muted/50"
                }`}
              >
                {DAY_LABELS[d]}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* 시간 선택 — 8개 슬롯 */}
      <div className="space-y-2">
        <Label>받고 싶은 시간</Label>
        <div className="flex flex-wrap gap-2">
          {DELIVERY_SLOTS.map((h) => (
            <button
              key={h}
              type="button"
              onClick={() => setHour(h)}
              disabled={isPending}
              className={`rounded-full px-3 py-1.5 text-sm border transition-colors ${
                hour === h ? "bg-primary text-primary-foreground border-primary" : "border-border hover:bg-muted/50"
              }`}
            >
              {hourLabel(h)}
            </button>
          ))}
        </div>
        <p className="text-xs text-muted-foreground">
          채널은 설정 시간에 거의 즉시, DM은 사용자별로 분산되어 약 30분 내에 순차 발송돼요.
        </p>
      </div>

      <Button onClick={handleSave} disabled={isPending}>
        {isPending ? "저장 중..." : "저장"}
      </Button>
    </div>
  );
}
