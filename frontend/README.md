# 💻 Student Skill Tracker - Frontend Client

> High-performance Angular 21 Standalone client application featuring Monaco Code Editor integration, WebSocket synchronization, and TailwindCSS 4.

The frontend client serves as the graphical interface for the Student Skill Tracker. Designed as a gamified coding portal, it offers a real-time multiplayer duel environment, sandboxed IDE panel, interactive cognitive/EQ tests, and dashboard analytics.

---

## 🎨 Technology Highlights

* **Angular 21**: Leveraging standalone components, new control flow syntax (`@if`, `@for`, `@switch`), and strict Server-Side Rendering (SSR) options.
* **TailwindCSS 4**: Utilizing modern CSS-first configuration and CSS post-processors to achieve premium UI designs (dark modes, micro-animations, glassmorphic widgets).
* **Monaco Code Editor (`ngx-monaco-editor-v2`)**: Embedding VS Code's editor engine directly in the browser to support syntax highlighting, custom auto-completion, key maps, and coding layout controls.
* **WebSocket Integration**: Connects to the Spring Boot server using `@stomp/stompjs` and `sockjs-client` to exchange real-time matchmaking queue events and code synchronization packets during duels.
* **State Management**: Built on `@ngrx/signals` for lightweight, reactive state tracking of student info, active lobbies, and websocket socket health.
* **Testing Suite**: Outfitted with `vitest` for rapid unit and component validation.

---

## 🧭 Directory Structure & Key Modules

The code is structured as a modular single-page application under [frontend/src/app](file:///C:/Users/Savyasachi%20Mishra/Desktop/student-skill-tracker/frontend/src/app):

```text
app/
├── advisor/            # Rishi AI Coach: Chat panels, study recommendations, calendar syncer
├── arsenal/            # The Arsenal: Integration setup cards for GitHub, LeetCode, Toggl, etc.
├── auth/               # User Authentication: Clean login/registration interfaces with validation
├── battle-station/     # Sandboxed Compiler: Monaco Editor, stdin inputs, execution telemetry
├── cognitive-sprint/   # Memory, attention, and logical tests with EQ profiling forms
├── core/               # Global guards (auth.guard), state stores (auth.store), and HTTP interceptors
├── dashboard/          # Performance Hub: Levels, XP trackers, active study modes, and stats graphs
├── duel-arena/         # Duel Arena: Lobby queues, live dual split-screen editor, code sync feeds
├── layout/             # Shell Layout: Sidebar navigation, profile status bar, active timers
├── leaderboard/        # Global Rankings: Interactive leaderboard of all active students
├── proving-grounds/    # Code Challenge Lists: Domain-specific algorithmic challenges
├── settings/           # Personal settings, security parameters, password change forms
├── app.config.ts       # Application routing configurations, hydration, and providers
└── app.routes.ts       # Route endpoints, lazy-loaded components, and guard configurations
```

---

## 🛠️ Key Component Guides

### 1. ⚔️ Duel Arena (`duel-arena.component.ts`)
* Configures WebSocket communication endpoints to match players together.
* Launches a dual-pane coding workspace. The player's pane contains the active Monaco Editor, while the opponent's pane renders a read-only live code comparison feed synchronized via STOMP messages.
* Renders real-time execution results, test cases passed, and countdown timers.

### 2. 🤖 Rishi AI Coach (`advisor.component.ts`)
* Interfaces with the Gemini AI backend to provide direct advice.
* Prompts include career pathing, custom resume critiques, and structured calendar schedules.
* Features a study timeline generator which calls backend routines to generate events and instantly post them to the user's Google Calendar.

### 3. 🧠 Cognitive Sprints (`cognitive-sprint.component.ts`)
* Implements client-side mini-games:
  * **Memory Matrix**: Grids of tiles that flash briefly and require the user to recall positions.
  * **Logic Challenge**: Speed-based boolean expression logic gates.
  * **Attention Check**: Color-word matching cognitive tests.
* Combines test scores with an EQ assessment questionnaire and saves telemetry back to the database.

### 4. 💻 Battle Station Compiler (`battle-station.component.ts`)
* Standard coding sandbox workspace.
* Provides custom tabs for code parameters: **Source**, **Standard Input**, **Standard Output**, **Compilation Diagnostics**, and **Performance Execution Info** (Runtime/Compile-Time duration).

---

## 🚀 Running the Client Locally

### 1. Installation
Install all dependencies using npm inside the `frontend` folder:
```bash
cd frontend
npm install
```

### 2. Run the Development Server
Starts a hot-reloading development server on port `4200`:
```bash
npm run start
```
Open [http://localhost:4200/](http://localhost:4200/) in your web browser.

### 3. Build & Compiling
Compiles the application with optimizations into the `dist/` directory:
```bash
npm run build
```

### 4. Run SSR Server (Production Build Verification)
To check how server-side rendering acts in production, build the app and execute:
```bash
npm run serve:ssr:frontend
```

### 5. Running Unit Tests
Executes unit tests utilizing the Vitest test runner:
```bash
npm run test
```
