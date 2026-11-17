import { useCallback, useEffect, useState } from 'react';
import type { CourseDetailResponse } from '@eduhub/api-client';
import { adminCourseApi } from '../api/courseApi';
import { isUnauthorized, messageOf } from '../api/apiError';
import { clearToken } from '../auth/adminSession';

/** 과정 상세(회차 포함) 훅. courseId가 없으면 조회하지 않는다. reload()로 다시 불러온다. */
export function useCourseDetail(courseId?: number) {
  const [data, setData] = useState<CourseDetailResponse>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [unauthorized, setUnauthorized] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (courseId === undefined) {
      setData(undefined);
      setError(undefined);
      return;
    }
    let ignore = false;
    setLoading(true);
    setError(undefined);

    adminCourseApi
      .getCourse(courseId)
      .then((response) => {
        if (!ignore) setData(response.data);
      })
      .catch((e: unknown) => {
        if (ignore) return;
        if (isUnauthorized(e)) {
          clearToken();
          setUnauthorized(true);
        }
        setError(messageOf(e, '과정 정보를 불러오지 못했습니다.'));
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [courseId, reloadKey]);

  const reload = useCallback(() => setReloadKey((k) => k + 1), []);

  return { data, loading, error, unauthorized, reload };
}
