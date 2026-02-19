import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, map, Observable, of, tap } from 'rxjs';
import { User } from './user.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private apiUrl = '/api';
  private tokenKey = 'student_skill_tracker_jwt';

  currentUser = signal<User | null>(null);
  isAuthenticated = signal<boolean>(false);

  private platformId = inject(PLATFORM_ID);

  constructor() {
    // Attempt to load user on startup only in the browser
    if (isPlatformBrowser(this.platformId)) {
      this.checkAuth().subscribe();
    }
  }

  login(credentials: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/auth/login`, credentials).pipe(
      tap(response => {
        if (response.token) {
          // Store token first
          localStorage.setItem(this.tokenKey, response.token);
          this.isAuthenticated.set(true);
          
          // Extract user info from token or response
          if (response.email) {
            // Create a basic user object from login response
            this.currentUser.set({
              id: 0, // Will be populated when dashboard data loads
              email: response.email,
              name: response.name || '',
              leetcodeUsername: response.leetcodeUsername || '',
              level: 0,
              xp: 0,
              roles: 'USER',
              skillData: undefined
            });
          }
        }
      }),
      catchError((error) => {
        console.error('Login error:', error);
        return of(null);
      })
    );
  }

  register(student: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/students/register`, student);
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    this.currentUser.set(null);
    this.isAuthenticated.set(false);
    this.router.navigate(['/login']);
  }

  checkAuth(): Observable<User | null> {
    // Only check auth if we have a token
    if (isPlatformBrowser(this.platformId)) {
      const token = localStorage.getItem(this.tokenKey);
      if (!token) {
        this.currentUser.set(null);
        this.isAuthenticated.set(false);
        return of(null);
      }
    }

    return this.http.get<any>(`${this.apiUrl}/auth/me`).pipe(
      map(response => {
        if (!response.authenticated) {
          this.currentUser.set(null);
          this.isAuthenticated.set(false);
          return null;
        }
        
        const user: User = {
          id: response.id,
          email: response.email,
          name: response.name,
          leetcodeUsername: response.leetcodeUsername,
          level: 0,
          xp: 0,
          roles: 'USER',
          skillData: undefined
        };
        this.currentUser.set(user);
        this.isAuthenticated.set(true);
        return user;
      }),
      catchError(() => {
        // Auth check failed - likely expired token, clear it
        if (isPlatformBrowser(this.platformId)) {
          localStorage.removeItem(this.tokenKey);
        }
        this.currentUser.set(null);
        this.isAuthenticated.set(false);
        return of(null);
      })
    );
  }
}
