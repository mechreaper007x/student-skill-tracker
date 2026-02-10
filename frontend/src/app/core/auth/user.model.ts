export interface SkillData {
  id?: number;
  problemSolvingScore?: number;
  algorithmsScore?: number;
  dataStructuresScore?: number;
  totalProblemsSolved?: number;
  easyProblems?: number;
  mediumProblems?: number;
  hardProblems?: number;
  ranking?: number;
  aiAdvice?: string;
}

export interface User {
  id: number;
  name: string;
  email: string;
  leetcodeUsername: string;
  level: number;
  xp: number;
  roles: string;
  skillData?: SkillData; // Optional: populated when fetching dashboard data
}

export interface AuthResponse {
  message?: string;
  error?: string;
  user?: User;
}
