import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Check, Loader2, Sparkles, Zap, Crown, ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { api, isAuthenticated } from "@/lib/api";
import { PlanResponse, SubscriptionResponse } from "@/lib/types";
import { useToast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";

export function Pricing() {
    const navigate = useNavigate();
    const { toast } = useToast();
    const [plans, setPlans] = useState<PlanResponse[]>([]);
    const [subscription, setSubscription] = useState<SubscriptionResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [checkoutLoading, setCheckoutLoading] = useState<number | null>(null);
    const [portalLoading, setPortalLoading] = useState(false);

    useEffect(() => {
        fetchData();
    }, []);

    const fetchData = async () => {
        try {
            const [plansData, subscriptionData] = await Promise.all([
                api.getPlans(),
                isAuthenticated() ? api.getSubscription() : Promise.resolve(null),
            ]);
            setPlans(plansData);
            setSubscription(subscriptionData);
        } catch (error) {
            console.error("Failed to fetch pricing data:", error);
            toast({
                title: "Error",
                description: "Failed to load pricing information.",
                variant: "destructive",
            });
        } finally {
            setLoading(false);
        }
    };

    const handleUpgrade = async (planId: number) => {
        if (!isAuthenticated()) {
            navigate("/login");
            return;
        }

        setCheckoutLoading(planId);
        try {
            const response = await api.createCheckout(planId);
            // Redirect to Stripe checkout
            window.location.href = response.checkoutUrl;
        } catch (error) {
            console.error("Failed to create checkout:", error);
            toast({
                title: "Error",
                description: "Failed to start checkout. Please try again.",
                variant: "destructive",
            });
        } finally {
            setCheckoutLoading(null);
        }
    };

    const handleManageSubscription = async () => {
        setPortalLoading(true);
        try {
            const response = await api.openCustomerPortal();
            window.location.href = response.portalUrl;
        } catch (error) {
            console.error("Failed to open portal:", error);
            toast({
                title: "Error",
                description: "Failed to open billing portal. Please try again.",
                variant: "destructive",
            });
        } finally {
            setPortalLoading(false);
        }
    };

    const getPlanIcon = (planName: string) => {
        const name = planName.toLowerCase();
        if (name.includes("enterprise") || name.includes("business")) {
            return <Crown className="w-6 h-6" />;
        }
        if (name.includes("pro") || name.includes("plus")) {
            return <Zap className="w-6 h-6" />;
        }
        return <Sparkles className="w-6 h-6" />;
    };

    const isCurrentPlan = (planId: number) => {
        return subscription?.plan?.id === planId;
    };

    if (loading) {
        return (
            <div className="min-h-screen bg-background flex items-center justify-center">
                <Loader2 className="w-8 h-8 animate-spin text-primary" />
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gradient-to-b from-background via-background to-primary/5">
            {/* Header */}
            <header className="border-b border-border/40 bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
                <div className="container flex h-14 max-w-screen-2xl items-center px-4 sm:px-8">
                    <Button variant="ghost" size="sm" onClick={() => navigate(-1)} className="gap-2">
                        <ArrowLeft className="w-4 h-4" />
                        Back
                    </Button>
                </div>
            </header>

            <main className="container max-w-screen-xl py-16 px-4 sm:px-8">
                {/* Hero Section */}
                <div className="text-center mb-16">
                    <h1 className="text-4xl md:text-5xl font-bold tracking-tight mb-4 bg-gradient-to-r from-primary via-purple-500 to-pink-500 bg-clip-text text-transparent">
                        Choose Your Plan
                    </h1>
                    <p className="text-lg text-muted-foreground max-w-2xl mx-auto">
                        Unlock the full potential of AI-powered development with our flexible pricing plans.
                    </p>
                </div>

                {/* Current Subscription Banner */}
                {subscription?.plan && (
                    <div className="mb-12 p-6 rounded-xl bg-gradient-to-r from-primary/10 via-purple-500/10 to-pink-500/10 border border-primary/20 text-center">
                        <p className="text-sm text-muted-foreground mb-1">Current Plan</p>
                        <p className="text-2xl font-bold text-primary">{subscription.plan.name}</p>
                        {subscription.currentPeriodEnd && (
                            <p className="text-sm text-muted-foreground mt-2">
                                {subscription.cancelAtPeriodEnd ? "Expires" : "Renews"} on{" "}
                                {new Date(subscription.currentPeriodEnd).toLocaleDateString()}
                            </p>
                        )}
                        <Button
                            variant="outline"
                            className="mt-4"
                            onClick={handleManageSubscription}
                            disabled={portalLoading}
                        >
                            {portalLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                            Manage Subscription
                        </Button>
                    </div>
                )}

                {/* Plans Grid */}
                {plans.length === 0 ? (
                    <div className="text-center py-20 border border-dashed rounded-lg">
                        <h3 className="text-lg font-semibold mb-2">No plans available</h3>
                        <p className="text-muted-foreground">
                            Please check back later or contact support.
                        </p>
                    </div>
                ) : (
                    <div className={cn(
                        "grid gap-8 mx-auto",
                        plans.length === 1 ? "max-w-md" :
                            plans.length === 2 ? "max-w-2xl grid-cols-1 md:grid-cols-2" :
                                "max-w-5xl grid-cols-1 md:grid-cols-2 lg:grid-cols-3"
                    )}>
                        {plans.map((plan, index) => {
                            const isCurrent = isCurrentPlan(plan.id);
                            const isPopular = index === Math.floor(plans.length / 2); // Middle plan is "popular"

                            return (
                                <Card
                                    key={plan.id}
                                    className={cn(
                                        "relative overflow-hidden transition-all duration-300 hover:shadow-xl",
                                        isPopular && "border-primary shadow-lg scale-105 z-10",
                                        isCurrent && "ring-2 ring-primary"
                                    )}
                                >
                                    {/* Popular Badge */}
                                    {isPopular && !isCurrent && (
                                        <div className="absolute top-0 right-0 bg-primary text-primary-foreground text-xs font-bold px-3 py-1 rounded-bl-lg">
                                            POPULAR
                                        </div>
                                    )}
                                    {isCurrent && (
                                        <div className="absolute top-0 right-0 bg-green-500 text-white text-xs font-bold px-3 py-1 rounded-bl-lg">
                                            CURRENT
                                        </div>
                                    )}

                                    <CardHeader className="text-center pb-2">
                                        <div className={cn(
                                            "mx-auto mb-4 w-14 h-14 rounded-full flex items-center justify-center",
                                            isPopular ? "bg-primary text-primary-foreground" : "bg-primary/10 text-primary"
                                        )}>
                                            {getPlanIcon(plan.name)}
                                        </div>
                                        <CardTitle className="text-2xl">{plan.name}</CardTitle>
                                        <CardDescription className="text-3xl font-bold text-foreground mt-2">
                                            {plan.Price}
                                            <span className="text-sm font-normal text-muted-foreground">/month</span>
                                        </CardDescription>
                                    </CardHeader>

                                    <CardContent className="pt-6">
                                        <ul className="space-y-3">
                                            <li className="flex items-center gap-3">
                                                <Check className="w-5 h-5 text-green-500 flex-shrink-0" />
                                                <span>{plan.maxProjects} Projects</span>
                                            </li>
                                            <li className="flex items-center gap-3">
                                                <Check className="w-5 h-5 text-green-500 flex-shrink-0" />
                                                <span>{plan.maxTokensPerDay.toLocaleString()} tokens/day</span>
                                            </li>
                                            {plan.unlimitedAi && (
                                                <li className="flex items-center gap-3">
                                                    <Check className="w-5 h-5 text-green-500 flex-shrink-0" />
                                                    <span>Unlimited AI requests</span>
                                                </li>
                                            )}
                                            <li className="flex items-center gap-3">
                                                <Check className="w-5 h-5 text-green-500 flex-shrink-0" />
                                                <span>Priority support</span>
                                            </li>
                                        </ul>
                                    </CardContent>

                                    <CardFooter className="pt-4">
                                        <Button
                                            className="w-full"
                                            variant={isPopular ? "default" : "outline"}
                                            size="lg"
                                            disabled={isCurrent || checkoutLoading !== null}
                                            onClick={() => handleUpgrade(plan.id)}
                                        >
                                            {checkoutLoading === plan.id && (
                                                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                            )}
                                            {isCurrent ? "Current Plan" : "Get Started"}
                                        </Button>
                                    </CardFooter>
                                </Card>
                            );
                        })}
                    </div>
                )}

                {/* Free Tier Info */}
                <div className="mt-16 text-center">
                    <p className="text-muted-foreground">
                        Not ready to upgrade?{" "}
                        <span className="text-foreground font-medium">
                            The free tier includes 1 project with essential features.
                        </span>
                    </p>
                </div>
            </main>
        </div>
    );
}
