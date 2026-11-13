import { redirect } from "next/navigation";
import { PortalShell } from "@/app/components/portal-shell";
import { readSession } from "@/lib/session/server";
export const dynamic = "force-dynamic";
export default async function OperationsLayout({children}:{children:React.ReactNode}) {
  const session=await readSession();
  if(!session) redirect("/login?next=/operations/dashboard");
  if(!session.user.roles.some(r=>["OPERATIONS","ADMIN"].includes(r))) redirect("/customer/dashboard");
  return <PortalShell kind="operations" user={session.user}>{children}</PortalShell>;
}
