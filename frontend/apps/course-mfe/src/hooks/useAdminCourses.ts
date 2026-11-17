import { useEffect, useState } from 'react';
import type { GetCoursesStatusEnum, PageResponseCourseSummaryResponse } from '@eduhub/api-client';
import { adminCourseApi } from '../api/courseApi';
import { isUnauthorized, messageOf } from '../api/apiError';
import { clearToken } from '../auth/adminSession';

export interface UseAdminCoursesParams {
  keyword?: string;
  status?: GetCoursesStatusEnum; // 생략하면 전체
  page: number; // 0부터 시작
  size: number;
  reloadKey?: number; // 바꾸면 같은 조건으로 다시 불러온다
}

/** 관리 화면의 과정 목록 훅. 늦게 도착한 이전 응답은 버린다. 401이면 토큰을 지우고 unauthorized를 켠다. */
export function useAdminCourses({ keyword, status, page, size, reloadKey = 0 }: UseAdminCoursesParams) {
  const [data, setData] = useState<PageResponseCourseSummaryResponse>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [unauthorized, setUnauthorized] = useState(false);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(undefined);

    // 생성된 인자 순서: (keyword, category, status, page, size)
    adminCourseApi
      .getCourses(keyword || undefined, undefined, status || undefined, page, size)
      .then((response) => {
        if (!ignore) setData(response.data);
      })
      .catch((e: unknown) => {
        if (ignore) return;
        if (isUnauthorized(e)) {
          clearToken();
          setUnauthorized(true);
        }
        setError(messageOf(e, '과정 목록을 불러오지 못했습니다.'));
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [keyword, status, page, size, reloadKey]);

  return { data, loading, error, unauthorized };
}
