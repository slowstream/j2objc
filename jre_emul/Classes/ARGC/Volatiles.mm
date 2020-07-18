#import "ARGC.h"

#define GC_DEBUG 0

#include "NSObject+ARGC.h"
#include <pthread.h>

static pthread_t g_lockThread = NULL;
static int g_cntLock = 0;

extern "C" {

static void lock_volatile() {
    pthread_t curr_thread = pthread_self();
    if (curr_thread != g_lockThread) {
        while (!__sync_bool_compare_and_swap(&g_lockThread, NULL, curr_thread)) {}
    }
    g_cntLock ++;
}

static void unlock_volatile() {
    if (GC_DEBUG) {
        assert(pthread_self() == g_lockThread);
    }
    if (--g_cntLock == 0) {
        g_lockThread = NULL;
    }
}

id JreLoadVolatileId(volatile_id *pVar) {
    lock_volatile();
    id oid = *(std::atomic<id>*)pVar;
    if (oid != NULL) {
        [oid retain];
        [oid autorelease];
    };
    unlock_volatile();
    return oid;
}

id JreAssignVolatileId(volatile_id *pVar, id newValue) {
    lock_volatile();
    ARGC_assignGenericObject((ARGC_FIELD_REF id*)pVar, newValue);
    unlock_volatile();

    return newValue;
}

void JreReleaseVolatile(volatile_id *pVar) {
    lock_volatile();
    ARGC_assignGenericObject((ARGC_FIELD_REF id*)pVar, NULL);
    unlock_volatile();
}

id JreVolatileStrongAssign(volatile_id *pVar, id newValue) {
  lock_volatile();
  ARGC_assignGenericObject((ARGC_FIELD_REF id*)pVar, newValue);
  unlock_volatile();
  return newValue;
}


id JreVolatileNativeAssign(volatile_id *pVar, id newValue) {
    lock_volatile();
    ARGC_assignStrongObject((ARGC_FIELD_REF id*)pVar, newValue);
    unlock_volatile();
    return newValue;
}

bool JreCompareAndSwapVolatileStrongId(volatile_id *ptr, id expected, id newValue) {
    std::atomic<id>* field = (std::atomic<id>*)ptr;
    lock_volatile();
    bool res =  field->compare_exchange_strong(expected, newValue);
    if (res) {
        if (newValue) ARGC_genericRetain(newValue);
        if (expected) ARGC_genericRelease(expected);
    }
    unlock_volatile();
    return res;
}

id JreExchangeVolatileStrongId(volatile_id *pVar, id newValue) {
    std::atomic<id>* field = (std::atomic<id>*)pVar;
    lock_volatile();
    id oldValue = field->exchange(newValue);
    if (oldValue != newValue) {
        if (newValue) {
            [newValue retain];
        }
        if (oldValue) {
//            if (GC_DEBUG && GC_LOG_ALLOC) {
//                if ([oldValue toJObject] == NULL) {
//                    NSLog(@"--nstr %p #%d %@", oldValue, (int)NSExtraRefCount(oldValue), [oldValue class]);
//                }
//            }
            [oldValue autorelease];
        }
    }
    unlock_volatile();
    return oldValue;
}

//void JreCloneVolatile(volatile_id *pVar, volatile_id *pOther) {
//    std::atomic<id>* pDst = (std::atomic<id>*)pVar;
//    std::atomic<id>* pSrc = (std::atomic<id>*)pOther;
//    id oid  = *pSrc;
//    *pDst = oid;
//}
//
//
//void JreCloneVolatileStrong(volatile_id *pVar, volatile_id *pOther) {
//    std::atomic<id>* pDst = (std::atomic<id>*)pVar;
//    std::atomic<id>* pSrc = (std::atomic<id>*)pOther;
//    id oid  = *pSrc;
//    [oid retain];
//    *pDst = oid;
//}
 
}



