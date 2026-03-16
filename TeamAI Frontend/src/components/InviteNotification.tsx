import { useState, useEffect } from "react";
import { Bell, Check, X, Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Badge } from "@/components/ui/badge";
import { api } from "@/lib/api";
import { PendingInvite } from "@/lib/types";
import { useToast } from "@/hooks/use-toast";

interface InviteNotificationProps {
    onInviteAccepted?: () => void;
}

export function InviteNotification({ onInviteAccepted }: InviteNotificationProps) {
    const [invites, setInvites] = useState<PendingInvite[]>([]);
    const [loading, setLoading] = useState(false);
    const [open, setOpen] = useState(false);
    const { toast } = useToast();

    const fetchInvites = async () => {
        try {
            const data = await api.getPendingInvites();
            setInvites(data);
        } catch (error) {
            console.error("Failed to fetch invites:", error);
        }
    };

    useEffect(() => {
        fetchInvites();
    }, []);

    const handleAccept = async (projectId: number, projectName: string) => {
        setLoading(true);
        try {
            await api.acceptInvite(projectId);
            toast({
                title: "Invite accepted",
                description: `You now have access to "${projectName}"`,
            });
            setInvites((prev) => prev.filter((i) => i.projectId !== projectId));
            onInviteAccepted?.();
        } catch (error) {
            toast({
                title: "Error",
                description: "Failed to accept invite",
                variant: "destructive",
            });
        } finally {
            setLoading(false);
        }
    };

    const handleDecline = async (projectId: number) => {
        setLoading(true);
        try {
            await api.declineInvite(projectId);
            toast({
                title: "Invite declined",
                description: "The invitation has been removed",
            });
            setInvites((prev) => prev.filter((i) => i.projectId !== projectId));
        } catch (error) {
            toast({
                title: "Error",
                description: "Failed to decline invite",
                variant: "destructive",
            });
        } finally {
            setLoading(false);
        }
    };

    const formatDate = (dateString: string) => {
        return new Date(dateString).toLocaleDateString("en-US", {
            month: "short",
            day: "numeric",
        });
    };

    return (
        <DropdownMenu open={open} onOpenChange={setOpen}>
            <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" className="relative">
                    <Bell className="h-5 w-5" />
                    {invites.length > 0 && (
                        <Badge
                            className="absolute -top-1 -right-1 h-5 w-5 flex items-center justify-center p-0 text-xs"
                            variant="destructive"
                        >
                            {invites.length}
                        </Badge>
                    )}
                </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-80">
                <div className="p-3 border-b">
                    <h3 className="font-semibold flex items-center gap-2">
                        <Users className="h-4 w-4" />
                        Project Invitations
                    </h3>
                </div>
                {invites.length === 0 ? (
                    <div className="p-4 text-center text-muted-foreground text-sm">
                        No pending invitations
                    </div>
                ) : (
                    <div className="max-h-80 overflow-y-auto">
                        {invites.map((invite) => (
                            <div
                                key={invite.projectId}
                                className="p-3 border-b last:border-b-0 hover:bg-muted/50"
                            >
                                <div className="flex items-start justify-between gap-2">
                                    <div className="flex-1 min-w-0">
                                        <p className="font-medium truncate">{invite.projectName}</p>
                                        <p className="text-xs text-muted-foreground">
                                            Invited by {invite.inviterName} • {formatDate(invite.invitedAt)}
                                        </p>
                                        <Badge variant="outline" className="mt-1 text-xs">
                                            {invite.role}
                                        </Badge>
                                    </div>
                                    <div className="flex gap-1 shrink-0">
                                        <Button
                                            size="icon"
                                            variant="ghost"
                                            className="h-8 w-8 text-green-600 hover:text-green-700 hover:bg-green-100"
                                            onClick={() => handleAccept(invite.projectId, invite.projectName)}
                                            disabled={loading}
                                        >
                                            <Check className="h-4 w-4" />
                                        </Button>
                                        <Button
                                            size="icon"
                                            variant="ghost"
                                            className="h-8 w-8 text-red-600 hover:text-red-700 hover:bg-red-100"
                                            onClick={() => handleDecline(invite.projectId)}
                                            disabled={loading}
                                        >
                                            <X className="h-4 w-4" />
                                        </Button>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
