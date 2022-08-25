package com.xiaofengstu.kill;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @ClassName Demo
 * @Author fengzeng
 * @Date 2022/8/5 0005 12:31
 */
public class Demo {
  /**
   * 启动 10 个线程
   * 库存 6 个
   * 生成一个合并队列
   * 每个用户能拿到自己的请求响应
   * @param args
   */
  public static void main(String[] args) throws InterruptedException {
    ExecutorService executor = Executors.newCachedThreadPool();
    Demo killDemo = new Demo();
    killDemo.mergeJob();
    Thread.sleep(2000);

    List<Future<Result>> futureList = new ArrayList<>();
    CountDownLatch countDownLatch = new CountDownLatch(10);

    for (int i = 0; i < 10; i++) {
      final Long orderId = i + 100L;
      final Long userId = (long) i;
      Future<Result> future = executor.submit(() ->{
        countDownLatch.countDown();
        return killDemo.operate(new UserRequest(orderId, userId, 1));
      });
      futureList.add(future);
    }
    countDownLatch.await();

    futureList.forEach(future->{
      try {
        Result result = future.get(300, TimeUnit.MILLISECONDS);
        System.out.println(Thread.currentThread().getName() + "客户端请求响应: " + result);
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        e.printStackTrace();
      }
    });

  }

  private Integer stock = 6;
  private BlockingQueue<RequestPromise> queue = new LinkedBlockingQueue<>(10);


  /**
   * 用户库存扣减操作
   * @param userRequest
   * @return
   */
  public Result operate(UserRequest userRequest) throws InterruptedException {
    // TODO 阈值判断
    // TODO 队列的创建
    RequestPromise requestPromise = new RequestPromise(userRequest);
    boolean offer = queue.offer(requestPromise, 100, TimeUnit.MILLISECONDS);
    if (!offer) {
      return new Result(false, "系统繁忙");
    }
    synchronized (requestPromise) {
      try {
        requestPromise.wait(200);
      }catch (InterruptedException e) {
        return new Result(false, "等待超时");
      }
    }
    return requestPromise.getResult();
  }

  public void mergeJob() {
    new Thread(() -> {
      List<RequestPromise> list = new ArrayList<>();
      while (true) {
        while (queue.isEmpty()) {
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }

        while (queue.peek() != null) {
          list.add(queue.poll());
        }

        System.out.println(Thread.currentThread().getName() + "合并扣减库存" + list);

        int sum = list.stream().mapToInt(e -> e.getUserRequest().getCount()).sum();
        // 两种情况
        // 第一种情况，库存足够
        if (sum <= stock) {
          stock -= sum;
          // notify all user
          list.forEach(requestPromise -> {
            requestPromise.setResult(new Result(true, "ok"));
            synchronized (requestPromise) {
              requestPromise.notify();
            }
          });
        }
        // 第二种情况，库存不够
        for (RequestPromise requestPromise : list) {
          int count = requestPromise.getUserRequest().getCount();
          if (count <= stock) {
            stock -= count;
            requestPromise.setResult(new Result(true, "ok"));
            synchronized (requestPromise) {
              requestPromise.notify();
            }
          } else {
            requestPromise.setResult(new Result(false, "库存不足"));
          }
        }
      }
    }, "mergeThread").start();
  }


}

class RequestPromise{
  private UserRequest userRequest;
  private Result result;

  public RequestPromise(UserRequest userRequest) {
    this.userRequest = userRequest;
  }

  public RequestPromise(UserRequest userRequest, Result result) {
    this.userRequest = userRequest;
    this.result = result;
  }

  public UserRequest getUserRequest() {
    return userRequest;
  }

  public void setUserRequest(UserRequest userRequest) {
    this.userRequest = userRequest;
  }

  public Result getResult() {
    return result;
  }

  public void setResult(Result result) {
    this.result = result;
  }

  @Override
  public String toString() {
    return "RequestPromise{" +
        "userRequest=" + userRequest +
        ", result=" + result +
        '}';
  }
}

class Result{
  private boolean success;
  private String msg;

  public Result(boolean success, String msg) {
    this.success = success;
    this.msg = msg;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getMsg() {
    return msg;
  }

  public void setMsg(String msg) {
    this.msg = msg;
  }

  @Override
  public String toString() {
    return "Result{" +
        "success=" + success +
        ", msg='" + msg + '\'' +
        '}';
  }
}

class UserRequest {
  private Long orderId;
  private Long userId;
  private Integer count;

  public UserRequest(Long orderId, Long userId, Integer count) {
    this.orderId = orderId;
    this.userId = userId;
    this.count = count;
  }

  public Long getOrderId() {
    return orderId;
  }

  public void setOrderId(Long orderId) {
    this.orderId = orderId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Integer getCount() {
    return count;
  }

  public void setCount(Integer count) {
    this.count = count;
  }

  @Override
  public String toString() {
    return "UserRequest{" +
        "orderId=" + orderId +
        ", userId=" + userId +
        ", count=" + count +
        '}';
  }
}
