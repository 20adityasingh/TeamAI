import { ChatMessage, DeployResponse, FileNode, LoginCredentials, LoginResponse, ProjectSummaryResponse, ProjectResponse, ProjectMember, ProjectRole, SignupRequest, AuthResponse, PendingInvite, SubscriptionResponse, CheckoutResponse, PortalResponse, PlanResponse } from "./types";

const BASE_URL = import.meta.env.VITE_API_URL || "http://team-ai.live";

export const getAuthToken = () => localStorage.getItem("auth_token");

export const setAuthToken = (token: string) => localStorage.setItem("auth_token", token);

export const removeAuthToken = () => localStorage.removeItem("auth_token");

export const isAuthenticated = () => !!getAuthToken();

const getAuthHeaders = (): HeadersInit => {
  const token = getAuthToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
};

// User info storage
export const setUserInfo = (user: { id: number; username: string; name: string }) => {
  localStorage.setItem("user_info", JSON.stringify(user));
};

export const getUserInfo = (): { id: number; username: string; name: string } | null => {
  const userInfo = localStorage.getItem("user_info");
  return userInfo ? JSON.parse(userInfo) : null;
};

export const removeUserInfo = () => localStorage.removeItem("user_info");

// LocalStorage keys
export const PREVIEW_URL_KEY = "preview_url";
export const OPEN_TABS_KEY = "open_tabs";
export const ACTIVE_TAB_KEY = "active_tab";

// API response format for files endpoint
interface FilesApiResponse {
  files: { path: string }[];
}

// Convert flat file paths to nested tree structure
function buildFileTree(paths: { path: string }[]): FileNode[] {
  const root: FileNode[] = [];
  const nodeMap = new Map<string, FileNode>();

  // Sort paths to ensure directories come before their children
  const sortedPaths = [...paths].sort((a, b) => a.path.localeCompare(b.path));

  for (const { path } of sortedPaths) {
    const parts = path.split("/");
    let currentPath = "";

    for (let i = 0; i < parts.length; i++) {
      const part = parts[i];
      const parentPath = currentPath;
      currentPath = currentPath ? `${currentPath}/${part}` : part;

      // Skip if node already exists
      if (nodeMap.has(currentPath)) continue;

      const isFile = i === parts.length - 1;
      const node: FileNode = {
        name: part,
        path: currentPath,
        type: isFile ? "file" : "directory",
        children: isFile ? undefined : [],
      };

      nodeMap.set(currentPath, node);

      if (parentPath) {
        const parent = nodeMap.get(parentPath);
        if (parent && parent.children) {
          parent.children.push(node);
        }
      } else {
        root.push(node);
      }
    }
  }

  // Sort each level: directories first, then alphabetically
  const sortNodes = (nodes: FileNode[]) => {
    nodes.sort((a, b) => {
      if (a.type === "directory" && b.type === "file") return -1;
      if (a.type === "file" && b.type === "directory") return 1;
      return a.name.localeCompare(b.name);
    });
    nodes.forEach((node) => {
      if (node.children) sortNodes(node.children);
    });
  };

  sortNodes(root);
  return root;
}

// Centralized API call helper
async function callApi<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const headers = {
    ...getAuthHeaders(),
    ...(options.headers || {}),
  };

  const response = await fetch(`${BASE_URL}${endpoint}`, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    // Token expired or invalid
    removeAuthToken();
    removeUserInfo();
    window.location.href = "/login";
    throw new Error("Session expired. Please login again.");
  }

  if (!response.ok) {
    const contentType = response.headers.get("content-type");
    let errorMessage = "API request failed";
    
    if (contentType && contentType.includes("application/json")) {
      const errorData = await response.json();
      errorMessage = errorData.message || errorData.error || errorMessage;
    } else {
      errorMessage = await response.text() || errorMessage;
    }
    
    throw new Error(errorMessage);
  }

  // Handle empty responses
  if (response.status === 204) {
    return {} as T;
  }

  return response.json();
}

export const api = {
  async login(credentials: LoginCredentials): Promise<LoginResponse> {
    const response = await fetch(`${BASE_URL}/api/v1/account/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(credentials),
    });

    if (!response.ok) {
      const error = await response.text();
      throw new Error(error || "Login failed");
    }

    return response.json();
  },

  async signup(data: SignupRequest): Promise<AuthResponse> {
    const response = await fetch(`${BASE_URL}/api/v1/account/auth/signup`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });

    if (!response.ok) {
      const error = await response.text();
      throw new Error(error || "Signup failed");
    }

    return response.json();
  },

  async getFiles(projectId: string): Promise<FileNode[]> {
    const data: FilesApiResponse = await callApi(`/api/v1/workspace/projects/${projectId}/files`);
    return buildFileTree(data.files);
  },

  async getFileContent(projectId: string, path: string): Promise<string> {
    const data = await callApi<{ content: string }>(
      `/api/v1/workspace/projects/${projectId}/files/content?path=${path}`
    );
    return data.content;
  },

  async deploy(projectId: string): Promise<DeployResponse> {
    return callApi(`/api/v1/workspace/projects/${projectId}/deploy`, {
      method: "POST",
    });
  },

  async getProjects(): Promise<ProjectSummaryResponse[]> {
    return callApi(`/api/v1/workspace/projects`);
  },

  async createProject(name: string): Promise<ProjectSummaryResponse> {
    return callApi(`/api/v1/workspace/projects`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name }),
    });
  },

  async getProject(id: string): Promise<ProjectResponse> {
    return callApi(`/api/v1/workspace/projects/${id}`);
  },

  async updateProject(id: string, name: string): Promise<ProjectResponse> {
    return callApi(`/api/v1/workspace/projects/${id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name }),
    });
  },

  async deleteProject(id: string): Promise<void> {
    return callApi(`/api/v1/workspace/projects/${id}`, {
      method: "DELETE",
    });
  },

  async downloadProjectZip(id: string): Promise<Blob> {
    // Backend endpoint does not exist yet
    console.warn("downloadProjectZip not implemented", id);
    throw new Error("Download ZIP not supported yet");
  },

  async getProjectMembers(projectId: string): Promise<ProjectMember[]> {
    return callApi(`/api/v1/workspace/projects/${projectId}/members`);
  },

  async inviteMember(projectId: string, username: string, role: ProjectRole): Promise<void> {
    return callApi(`/api/v1/workspace/projects/${projectId}/members`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, role }),
    });
  },

  async updateMemberRole(projectId: string, userId: number, role: ProjectRole): Promise<void> {
    return callApi(`/api/v1/workspace/projects/${projectId}/members/${userId}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ role }),
    });
  },

  async removeMember(projectId: string, userId: number): Promise<void> {
    return callApi(`/api/v1/workspace/projects/${projectId}/members/${userId}`, {
      method: "DELETE",
    });
  },

  // Invites
  async getPendingInvites(): Promise<PendingInvite[]> {
    return callApi(`/api/v1/workspace/invites`);
  },

  async acceptInvite(projectId: number): Promise<void> {
    return callApi(`/api/v1/workspace/invites/${projectId}/accept`, {
      method: "POST",
    });
  },

  async declineInvite(projectId: number): Promise<void> {
    return callApi(`/api/v1/workspace/invites/${projectId}`, {
      method: "DELETE",
    });
  },

  // Payment & Subscription
  async getPlans(): Promise<PlanResponse[]> {
    try {
      return await callApi(`/api/v1/account/plans`);
    } catch (e) {
      console.warn("Failed to fetch plans", e);
      return []; // Fallback empty
    }
  },

  // Billing
  async getSubscription(): Promise<SubscriptionResponse> {
    return callApi(`/api/v1/account/me/subscription`);
  },

  async createCheckout(planId: number): Promise<CheckoutResponse> {
    return callApi(`/api/v1/account/payment/checkout`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ planId }),
    });
  },

  async openCustomerPortal(): Promise<PortalResponse> {
    return callApi(`/api/v1/account/payment/portal`, {
      method: "POST",
    });
  },

  async getChatHistory(projectId: string): Promise<ChatMessage[]> {
    return callApi(`/api/v1/intelligence/chat/projects/${projectId}`);
  },

  async streamChat(
      projectId: string,
      content: string,
      onChunk: (text: string) => void,
      onFile: (path: string, content: string) => void,
      onComplete: () => void,
      onError: (error: Error) => void,
      signal?: AbortSignal
  ) {
    const localController = signal ? undefined : new AbortController();
    const effectiveSignal = signal || localController?.signal;

    fetch(`${BASE_URL}/api/v1/intelligence/chat/stream`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...getAuthHeaders() },
      body: JSON.stringify({ message: content, projectId }),
      signal: effectiveSignal,
    })
        .then(async (response) => {
          if (response.status === 401) {
            removeAuthToken();
            removeUserInfo();
            window.location.href = "/login";
            throw new Error("Session expired. Please login again.");
          }

          if (!response.ok) {
            const errorText = await response.text();
            let errorMessage = "Chat stream failed";
            try {
              const errorJson = JSON.parse(errorText);
              if (errorJson.message) errorMessage = errorJson.message;
            } catch (e) {
              if (errorText) errorMessage = errorText;
            }
            throw new Error(errorMessage);
          }

          const reader = response.body?.getReader();
          if (!reader) throw new Error("No reader available");

          const decoder = new TextDecoder();
          let sseBuffer = "";
          let fullContentBuffer = "";

          while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            const chunk = decoder.decode(value, { stream: true });
            sseBuffer += chunk;

            const lines = sseBuffer.split("\n");
            sseBuffer = lines.pop() || "";

            for (const line of lines) {
              const trimmedLine = line.trim();
              if (!trimmedLine || !trimmedLine.startsWith("data:")) continue;

              const dataStr = line.slice(5).trim();
              if (!dataStr) continue;

              let content = dataStr;
              try {
                const parsed = JSON.parse(dataStr);
                if (typeof parsed === "string") {
                  content = parsed;
                } else if (parsed && typeof parsed === "object" && "text" in parsed) {
                  content = parsed.text;
                }
              } catch (_) {
                // treat as plain text
              }

              if (!content) continue;

              onChunk(content);
              fullContentBuffer += content;

              const fileRegex = /<file\s+path=(?:"([^"]+)"|'([^']+)'|([^\s>]+))\s*>([\s\S]*?)<\/file>/gi;
              let match;
              while ((match = fileRegex.exec(fullContentBuffer)) !== null) {
                const path = match[1] || match[2] || match[3];
                let fileContent = match[4].trim();
                fileContent = fileContent
                    .replace(/^```[a-zA-Z]*\n?/i, '')
                    .replace(/\n?```$/i, '')
                    .trim();
                if (path && fileContent) onFile(path, fileContent);
              }
            }
          }

          if (sseBuffer.trim().startsWith("data:")) {
            const dataStr = sseBuffer.trim().slice(5).trim();
            if (dataStr) {
              let content = dataStr;
              try {
                const parsed = JSON.parse(dataStr);
                if (typeof parsed === "string") content = parsed;
                else if (parsed && typeof parsed === "object" && "text" in parsed) {
                  content = parsed.text;
                }
              } catch (_) {}

              if (content) {
                onChunk(content);
                fullContentBuffer += content;
                const fileRegex = /<file\s+path=(?:"([^"]+)"|'([^']+)'|([^\s>]+))\s*>([\s\S]*?)<\/file>/gi;
                let match;
                while ((match = fileRegex.exec(fullContentBuffer)) !== null) {
                  const path = match[1] || match[2] || match[3];
                  let fileContent = match[4].trim().replace(/^```[a-zA-Z]*\n?/i, '').replace(/\n?```$/i, '').trim();
                  if (path && fileContent) onFile(path, fileContent);
                }
              }
            }
          }

          onComplete();
        })
        .catch((error) => {
          if (error.name !== "AbortError") {
            console.error("Stream error:", error);
            onError(error);
          }
        });

    return () => {
      if (localController) {
        localController.abort();
      }
    };
  }

};
