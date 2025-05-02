Connect 4 Multiplayer Game
CS 342 – Software Design
University of Illinois Chicago
Feb 2025 – Apr 2025

Overview:
Developed a networked multiplayer version of Connect 4 using Java and JavaFX. The application enables multiple clients to connect to a centralized server, play real-time matches, and track game outcomes. Built with a focus on design patterns, client-server architecture, and responsive GUI design.

Features:
- Two-player online Connect 4 gameplay with turn-based logic and win detection

- Turn timer with automatic forfeits for inactivity

- Game lobby system to host or join matches

- In-game messaging for real-time communication

- Leaderboard displaying win/loss history

- User login and logout functionality with persistent data storage

Technical Highlights:
- Implemented using JavaFX for GUI and sockets for network communication

- Applied Template Method and Adapter design patterns to enforce extensible structure

- Used multi-threading for concurrent game management and server-side scalability

- Stored user data in flat file for simplicity and portability

- Built-in timer logic using java.util.Timer and TimerTask for turn handling

Development Process:
- Designed initial GUI mockups and class structure

- Developed core gameplay loop and server-client messaging protocol

- Integrated design patterns to promote modularity and maintainability

- Tested multiplayer stability and edge cases like disconnection, timeouts, and simultaneous input

- Conducted demo presentations to instructors and peers, gathering live feedback

Tools & Technologies:
- Java, JavaFX

- TCP/IP Sockets

- Multi-threading

- OOP Principles

- File I/O

Future Plans: 
- Migrate user data storage to SQLite or MySQL

- Add support for AI opponents and difficulty levels

- Polish GUI with animations and visual effects

- Expand leaderboard to include global rankings
