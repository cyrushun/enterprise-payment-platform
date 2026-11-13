import { redirect } from "next/navigation";
import { PortalShell } from "@/app/components/portal-shell";
import { readSession } from "@/lib/session/server";
export const dynamic = "force-dynamic";
export default async function CustomerLayout({children}:{children:React.ReactNode}) {
  const session=await readSession();
  if(!session) redirect("/login?next=/customer/dashboard");
  if(!session.user.roles.includes("CUSTOMER")) redirect(session.user.roles.some(r=>["OPERATIONS","ADMIN"].includes(r))?"/operations/dashboard":"/login");
  return <PortalShell kind="customer" user={session.user}>{children}</PortalShell>;
}
