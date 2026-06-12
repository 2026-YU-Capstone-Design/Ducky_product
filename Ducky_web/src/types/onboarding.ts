export type LearningAxis = "processing" | "structure" | "representation";

export interface OnboardingQuestion {
  id: number;
  axis: LearningAxis;
  text: string;
  optionA: {
    text: string;
    value: string;
  };
  optionB: {
    text: string;
    value: string;
  };
}

export interface LearningStyleResult {
  processingStyle: string;
  structureStyle: string;
  representationStyle: string;
}
