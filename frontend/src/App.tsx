import { RouterProvider } from "react-router-dom";
import { Toaster } from "@/components/ui/sonner";
import { StaleEditModalHost } from "@/components/shared/StaleEditModalHost";
import { router } from "@/router";

export default function App() {
  return (
    <>
      <RouterProvider router={router} />
      <Toaster position="top-center" closeButton />
      <StaleEditModalHost />
    </>
  );
}
