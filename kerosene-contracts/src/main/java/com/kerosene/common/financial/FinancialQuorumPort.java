package com.kerosene.common.financial;

public interface FinancialQuorumPort {

    Result requireHealthyUnanimousConsensus(String proposalHash);

    record Result(int acceptedNodes, int totalHealthyNodes) {
    }
}
