export type Pattern = string;
export type Path = string;
export type KeyedPatterns<T> = {
    readonly [K in keyof T]: Pattern[];
};
export type KeyedPaths<T> = {
    readonly [K in keyof T]: Path[];
};
export type _MatchResult<T> = {
    readonly [K in keyof T]: boolean;
};
export type MatchResult<T> = _MatchResult<T> & {
    /** Returns an object containing arrays of matched files instead of the usual boolean values. */
    getKeyedPaths(): KeyedPaths<T>;
};
/** A vendored copy of the  Chainsmoker module on NPM */
export type Chainsmoker<T> = (...patterns: Pattern[]) => MatchResult<T>;
export default function chainsmoker<T>(keyedPaths: KeyedPaths<T>): Chainsmoker<T>;
