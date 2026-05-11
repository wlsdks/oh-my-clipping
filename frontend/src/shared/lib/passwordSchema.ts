import { z } from "zod";

export const passwordSchema = z
  .string()
  .min(8, "비밀번호는 8자 이상이어야 해요")
  .regex(/[A-Za-z]/, "영문을 1자 이상 포함해야 해요")
  .regex(/[0-9]/, "숫자를 1자 이상 포함해야 해요");
